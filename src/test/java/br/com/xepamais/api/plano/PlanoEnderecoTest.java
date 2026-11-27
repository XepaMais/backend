package br.com.xepamais.api.plano;

import br.com.xepamais.api.TokenDaRota;
import br.com.xepamais.api.auth.SessaoService;
import br.com.xepamais.api.auth.PasswordHasher;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Plano do feirante e endereço de entrega do consumidor guardados no banco.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:planos;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
class PlanoEnderecoTest {

    @Autowired WebApplicationContext contexto;
    @Autowired SessaoService tokens;
    @Autowired PasswordHasher senhas;
    @Autowired ContaRepository contaRepository;
    @Autowired FeiranteRepository feiranteRepository;
    @Autowired ConsumidorRepository consumidorRepository;
    @Autowired FeiraRepository feiraRepository;

    MockMvc mvc;
    Conta contaFeirante;
    Integer contaConsumidor;
    Integer idFeira;

    @BeforeEach
    void preparar() {
        mvc = TokenDaRota.mockMvc(contexto, tokens);
        Feira feira = new Feira();
        feira.setNome("Feira " + UUID.randomUUID().toString().substring(0, 6));
        feira.setEndereco("Rua Clélia");
        feira.setCidade("São Paulo");
        feira.setUf("SP");
        feira = feiraRepository.save(feira);
        idFeira = feira.getId();

        contaFeirante = conta("Feirante");
        Feirante feirante = new Feirante();
        feirante.setConta(contaFeirante);
        feirante.setNomeFantasia("Barraca");
        feirante.setFeiras(new HashSet<>(Set.of(feira)));
        feiranteRepository.save(feirante);

        Consumidor consumidor = new Consumidor();
        consumidor.setConta(conta("Consumidor"));
        consumidorRepository.save(consumidor);
        contaConsumidor = consumidor.getConta().getId();
    }

    private Conta conta(String nome) {
        Conta conta = new Conta();
        conta.setNome(nome);
        conta.setEmail(UUID.randomUUID() + "@teste.com");
        conta.setSenha(senhas.hash("senha1234"));
        return contaRepository.save(conta);
    }

    private String login(Conta conta) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + conta.getEmail() + "\",\"senha\":\"senha1234\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void planosPadraoExistemEOFeiranteEscolheOPlano() throws Exception {
        mvc.perform(get("/api/planos"))
                .andExpect(jsonPath("$[*].codigo", contains("gratis", "crescer", "pro")))
                .andExpect(jsonPath("$[0].nome").value("Semente"))
                .andExpect(jsonPath("$[0].percentualComissao").value(15.0))
                .andExpect(jsonPath("$[1].valor").value(29.9));

        String plano = "/api/feirante/" + contaFeirante.getId() + "/plano";
        // Ainda não escolheu: o login avisa (plano null) e vale o Semente.
        String antes = login(contaFeirante);
        org.junit.jupiter.api.Assertions.assertNull(JsonPath.read(antes, "$.plano"));
        mvc.perform(get(plano))
                .andExpect(jsonPath("$.escolhido").value(false))
                .andExpect(jsonPath("$.plano.codigo").value("gratis"));

        mvc.perform(put(plano).contentType(MediaType.APPLICATION_JSON).content("{\"codigo\":\"inexistente\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put(plano).contentType(MediaType.APPLICATION_JSON).content("{\"codigo\":\"crescer\"}"))
                .andExpect(jsonPath("$.escolhido").value(true))
                .andExpect(jsonPath("$.plano.nome").value("Broto"));

        // Em outro aparelho o login já traz o plano.
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + contaFeirante.getEmail() + "\",\"senha\":\"senha1234\"}"))
                .andExpect(jsonPath("$.plano").value("crescer"));

        // A venda passa a ter a comissão do Broto (8% de 20,00 = 1,60): o feirante recebe 18,40.
        String xepa = mvc.perform(post("/api/feirante/" + contaFeirante.getId() + "/xepas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Xepa\",\"categoria\":\"Frutas\",\"preco\":10,\"quantidade\":5,\"idFeira\":"
                                + idFeira + "}"))
                .andReturn().getResponse().getContentAsString();
        Integer idXepa = JsonPath.read(xepa, "$.id");
        mvc.perform(post("/api/consumidor/" + contaConsumidor + "/pedidos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itens\":[{\"idXepa\":" + idXepa + ",\"quantidade\":2}]}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/financeiro/" + contaFeirante.getId() + "/rendas"))
                .andExpect(jsonPath("$[0].valor").value(18.4));
    }

    @Test
    void enderecoDeEntregaFicaNaConta() throws Exception {
        String endereco = "/api/consumidor/" + contaConsumidor + "/endereco";
        mvc.perform(get(endereco))
                .andExpect(jsonPath("$.tipo").value("retirada"))
                .andExpect(jsonPath("$.endereco.cep").value(""));

        // Salvo pela metade enquanto a pessoa digita.
        mvc.perform(put(endereco).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tipo\":\"entrega\",\"endereco\":{\"cep\":\"05042-000\",\"logradouro\":\"Rua A\"}}"))
                .andExpect(jsonPath("$.tipo").value("entrega"))
                .andExpect(jsonPath("$.endereco.cep").value("05042000"))
                .andExpect(jsonPath("$.endereco.numero").value(""));

        mvc.perform(put(endereco).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endereco\":{\"uf\":\"S1\"}}"))
                .andExpect(status().isBadRequest());

        // A compra com entrega guarda o endereço completo usado.
        String xepa = mvc.perform(post("/api/feirante/" + contaFeirante.getId() + "/xepas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Xepa\",\"categoria\":\"Frutas\",\"preco\":10,\"quantidade\":5,\"idFeira\":"
                                + idFeira + "}"))
                .andReturn().getResponse().getContentAsString();
        Integer idXepa = JsonPath.read(xepa, "$.id");
        mvc.perform(post("/api/consumidor/" + contaConsumidor + "/pedidos").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"itens":[{"idXepa":%d,"quantidade":1}],
                                 "recebimento":{"tipo":"entrega","endereco":{"cep":"01310-100","logradouro":"Av. Paulista",
                                 "numero":"1000","complemento":"ap 5","bairro":"Bela Vista","cidade":"São Paulo","uf":"sp"}}}"""
                                .formatted(idXepa)))
                .andExpect(status().isCreated());
        mvc.perform(get(endereco))
                .andExpect(jsonPath("$.tipo").value("entrega"))
                .andExpect(jsonPath("$.endereco.logradouro").value("Av. Paulista"))
                .andExpect(jsonPath("$.endereco.uf").value("SP"))
                .andExpect(jsonPath("$.endereco.complemento").value("ap 5"));

        // Outra conta não acessa.
        MockMvcBuilders.webAppContextSetup(contexto).build()
                .perform(get(endereco).with(TokenDaRota.comoConta(tokens, contaFeirante.getId())))
                .andExpect(status().isForbidden());
    }
}
