package br.com.xepamais.api.xepa;

import br.com.xepamais.api.TokenDaRota;
import br.com.xepamais.api.auth.SessaoService;
import br.com.xepamais.api.database.model.Conta;
import br.com.xepamais.api.database.model.Consumidor;
import br.com.xepamais.api.database.model.Feira;
import br.com.xepamais.api.database.model.Feirante;
import br.com.xepamais.api.database.repository.ConsumidorRepository;
import br.com.xepamais.api.database.repository.ContaRepository;
import br.com.xepamais.api.database.repository.FeiraRepository;
import br.com.xepamais.api.database.repository.FeiranteRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Feirante edita/exclui xepas; consumidor e feirante cancelam pedidos.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:edicao;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
class EdicaoCancelamentoTest {

    @Autowired WebApplicationContext contexto;
    @Autowired SessaoService tokens;
    @Autowired ContaRepository contaRepository;
    @Autowired FeiranteRepository feiranteRepository;
    @Autowired ConsumidorRepository consumidorRepository;
    @Autowired FeiraRepository feiraRepository;

    MockMvc mvc;
    Integer contaFeirante;
    Integer contaConsumidor;
    Integer idFeira;

    @BeforeEach
    void preparar() {
        mvc = TokenDaRota.mockMvc(contexto, tokens);
        Feira feira = new Feira();
        feira.setNome("Feira " + UUID.randomUUID().toString().substring(0, 6));
        feira.setEndereco("Rua A");
        feira.setCidade("São Paulo");
        feira.setUf("SP");
        feira = feiraRepository.save(feira);
        idFeira = feira.getId();

        Feirante feirante = new Feirante();
        feirante.setConta(conta("Fabiane"));
        feirante.setNomeFantasia("Barraca");
        feirante.setFeiras(new HashSet<>(Set.of(feira)));
        feiranteRepository.save(feirante);
        contaFeirante = feirante.getConta().getId();

        Consumidor consumidor = new Consumidor();
        consumidor.setConta(conta("Henrique"));
        consumidorRepository.save(consumidor);
        contaConsumidor = consumidor.getConta().getId();
    }

    private Conta conta(String nome) {
        Conta conta = new Conta();
        conta.setNome(nome);
        conta.setEmail(UUID.randomUUID() + "@teste.com");
        conta.setSenha("x");
        return contaRepository.save(conta);
    }

    private Integer publicar(int quantidade) throws Exception {
        String xepa = mvc.perform(post("/api/feirante/" + contaFeirante + "/xepas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Xepa\",\"categoria\":\"Frutas\",\"itens\":\"Banana\",\"descricao\":\"Doce\","
                                + "\"preco\":10,\"quantidade\":" + quantidade + ",\"idFeira\":" + idFeira + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(xepa, "$.id");
    }

    private String comprar(Integer idXepa, int quantidade) throws Exception {
        return mvc.perform(post("/api/consumidor/" + contaConsumidor + "/pedidos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itens\":[{\"idXepa\":" + idXepa + ",\"quantidade\":" + quantidade + "}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void feiranteEditaEExcluiXepa() throws Exception {
        Integer idXepa = publicar(2);
        String xepa = "/api/feirante/" + contaFeirante + "/xepas/" + idXepa;

        // Só os campos enviados mudam; quantidade 0 = esgotada (sai da vitrine).
        mvc.perform(put(xepa).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preco\":7.5,\"quantidade\":0,\"itens\":\"Banana e maçã\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preco").value(7.5))
                .andExpect(jsonPath("$.status").value("ESGOTADA"))
                .andExpect(jsonPath("$.itens").value("Banana e maçã"))
                .andExpect(jsonPath("$.descricao").value("Doce"))
                .andExpect(jsonPath("$.nome").value("Xepa"));
        mvc.perform(get("/api/xepas")).andExpect(jsonPath("$[*].id", not(hasItem(idXepa))));

        // Repor a quantidade volta para a vitrine.
        mvc.perform(put(xepa).contentType(MediaType.APPLICATION_JSON).content("{\"quantidade\":3}"))
                .andExpect(jsonPath("$.status").value("DISPONIVEL"));
        mvc.perform(get("/api/xepas")).andExpect(jsonPath("$[*].id", hasItem(idXepa)));

        mvc.perform(put(xepa).contentType(MediaType.APPLICATION_JSON).content("{\"preco\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put(xepa).contentType(MediaType.APPLICATION_JSON).content("{\"categoria\":\"Pão\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(delete(xepa)).andExpect(status().isNoContent());
        mvc.perform(get("/api/xepas/" + idXepa)).andExpect(status().isNotFound());
        mvc.perform(put(xepa).contentType(MediaType.APPLICATION_JSON).content("{\"quantidade\":1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void consumidorCancelaEOEstoqueVolta() throws Exception {
        Integer idXepa = publicar(2);
        String pedidos = comprar(idXepa, 2);
        Integer idPedido = JsonPath.read(pedidos, "$[0].id");
        Integer idConversa = JsonPath.read(pedidos, "$[0].idConversa");
        // Comprou tudo: esgotou e a venda entrou no financeiro.
        mvc.perform(get("/api/xepas")).andExpect(jsonPath("$[*].id", not(hasItem(idXepa))));
        mvc.perform(get("/api/financeiro/" + contaFeirante + "/rendas")).andExpect(jsonPath("$", hasSize(1)));

        String cancelar = "/api/consumidor/" + contaConsumidor + "/pedidos/" + idPedido + "/cancelar";
        mvc.perform(post(cancelar))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Cancelado"))
                .andExpect(jsonPath("$.situacao").value("CANCELADO"));

        mvc.perform(get("/api/xepas/" + idXepa))
                .andExpect(jsonPath("$.quantidade").value(2))
                .andExpect(jsonPath("$.status").value("DISPONIVEL"));
        mvc.perform(get("/api/financeiro/" + contaFeirante + "/rendas")).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/feirante/" + contaFeirante + "/conversas/" + idConversa))
                .andExpect(jsonPath("$.status").value("finalizada"));
        // Cancelado não entra no impacto e não cancela de novo.
        mvc.perform(get("/api/consumidor/" + contaConsumidor + "/impacto")).andExpect(jsonPath("$.pedidos").value(0));
        mvc.perform(post(cancelar)).andExpect(status().isConflict());
    }

    @Test
    void pedidoRetiradoNaoCancelaEFeiranteTambemCancela() throws Exception {
        Integer idXepa = publicar(5);
        String primeiro = comprar(idXepa, 1);
        Integer idRetirado = JsonPath.read(primeiro, "$[0].id");
        String codigo = JsonPath.read(primeiro, "$[0].codigo");
        mvc.perform(post("/api/feirante/" + contaFeirante + "/pedidos/" + idRetirado + "/retirada")
                .contentType(MediaType.APPLICATION_JSON).content("{\"codigo\":\"" + codigo + "\"}"));
        mvc.perform(post("/api/consumidor/" + contaConsumidor + "/pedidos/" + idRetirado + "/cancelar"))
                .andExpect(status().isConflict());

        Integer idOutro = JsonPath.read(comprar(idXepa, 2), "$[0].id");
        mvc.perform(post("/api/feirante/" + contaFeirante + "/pedidos/" + idOutro + "/cancelar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Cancelado"))
                .andExpect(jsonPath("$.codigo").doesNotExist());
        mvc.perform(get("/api/xepas/" + idXepa)).andExpect(jsonPath("$.quantidade").value(4));
    }
}
