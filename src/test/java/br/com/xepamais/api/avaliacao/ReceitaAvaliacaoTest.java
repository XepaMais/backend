package br.com.xepamais.api.avaliacao;

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

// Receitas da Cozinha vindas do banco e avaliação dos pedidos.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:avaliacoes;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
class ReceitaAvaliacaoTest {

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

        Conta contaF = conta("Fabiane Souza");
        contaF.setFoto("/api/imagens/1");
        contaRepository.save(contaF);
        Feirante feirante = new Feirante();
        feirante.setConta(contaF);
        feirante.setNomeFantasia("Barraca da Fabi");
        feirante.setFeiras(new HashSet<>(Set.of(feira)));
        feiranteRepository.save(feirante);
        contaFeirante = contaF.getId();

        Consumidor consumidor = new Consumidor();
        consumidor.setConta(conta("Henrique Mazza"));
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

    @Test
    void receitasVemDoBanco() throws Exception {
        mvc.perform(get("/api/receitas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(9))))
                .andExpect(jsonPath("$[?(@.codigo == 'caldo-verde')].nome", contains("Caldo verde")))
                .andExpect(jsonPath("$[?(@.codigo == 'caldo-verde')].tempoMinutos", contains(35)))
                .andExpect(jsonPath("$[?(@.codigo == 'caldo-verde')].ingredientes[*]", contains("couve", "batata", "cebola")));
        mvc.perform(get("/api/receitas").param("categoria", "sopas"))
                .andExpect(jsonPath("$[*].categoria", everyItem(is("Sopas"))))
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void consumidorAvaliaDepoisDeRetirar() throws Exception {
        String xepa = mvc.perform(post("/api/feirante/" + contaFeirante + "/xepas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Xepa\",\"categoria\":\"Frutas\",\"preco\":10,\"quantidade\":5,\"idFeira\":"
                                + idFeira + "}"))
                .andReturn().getResponse().getContentAsString();
        Integer idXepa = JsonPath.read(xepa, "$.id");
        String pedidos = mvc.perform(post("/api/consumidor/" + contaConsumidor + "/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itens\":[{\"idXepa\":" + idXepa + ",\"quantidade\":1}]}"))
                .andReturn().getResponse().getContentAsString();
        Integer idPedido = JsonPath.read(pedidos, "$[0].id");
        String codigo = JsonPath.read(pedidos, "$[0].codigo");
        String avaliacao = "/api/consumidor/" + contaConsumidor + "/pedidos/" + idPedido + "/avaliacao";

        // Antes de retirar não dá.
        mvc.perform(put(avaliacao).contentType(MediaType.APPLICATION_JSON).content("{\"nota\":5}"))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/feirante/" + contaFeirante + "/pedidos/" + idPedido + "/retirada")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"codigo\":\"" + codigo + "\"}"))
                .andExpect(status().isOk());

        mvc.perform(put(avaliacao).contentType(MediaType.APPLICATION_JSON).content("{\"nota\":6}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put(avaliacao).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nota\":5,\"comentario\":\"Tudo fresquinho!\"}"))
                .andExpect(jsonPath("$.avaliacao.nota").value(5))
                .andExpect(jsonPath("$.avaliacao.comentario").value("Tudo fresquinho!"));
        // Avaliar de novo troca a nota.
        mvc.perform(put(avaliacao).contentType(MediaType.APPLICATION_JSON).content("{\"nota\":4}"))
                .andExpect(jsonPath("$.avaliacao.nota").value(4))
                .andExpect(jsonPath("$.avaliacao.comentario").doesNotExist());

        mvc.perform(get("/api/consumidor/" + contaConsumidor + "/pedidos"))
                .andExpect(jsonPath("$[0].avaliacao.nota").value(4));
        mvc.perform(get("/api/feirante/" + contaFeirante + "/pedidos"))
                .andExpect(jsonPath("$[0].avaliacao.nota").value(4));

        // Perfil público (sem login): média, total e primeiro nome de quem avaliou.
        mvc.perform(get("/api/feirantes/" + contaFeirante))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.barraca").value("Barraca da Fabi"))
                .andExpect(jsonPath("$.foto").value("/api/imagens/1"))
                .andExpect(jsonPath("$.media").value(4.0))
                .andExpect(jsonPath("$.totalAvaliacoes").value(1))
                .andExpect(jsonPath("$.avaliacoes[0].consumidor").value("Henrique"));
    }
}
