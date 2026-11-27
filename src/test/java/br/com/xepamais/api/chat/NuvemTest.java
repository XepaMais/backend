package br.com.xepamais.api.chat;

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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Dados na nuvem: login com token, fotos no banco, perfil completo e chat entre consumidor e feirante.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:nuvem;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
@AutoConfigureMockMvc
class NuvemTest {

    // PNG 1x1 transparente.
    private static final String PNG = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==";

    @Autowired MockMvc semToken;
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
        feira.setEndereco("Rua Clélia");
        feira.setCidade("São Paulo");
        feira.setUf("SP");
        feira = feiraRepository.save(feira);
        idFeira = feira.getId();

        Feirante feirante = new Feirante();
        feirante.setConta(conta("Fabiane"));
        feirante.setNomeFantasia("Barraca da Fabiane");
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
        conta.setCpf(String.valueOf(10_000_000_000L + (long) (Math.random() * 89_999_999_999L)));
        return contaRepository.save(conta);
    }

    @Test
    void rotasDaContaExigemTokenDaMesmaConta() throws Exception {
        String carrinho = "/api/consumidor/" + contaConsumidor + "/carrinho";
        semToken.perform(get(carrinho)).andExpect(status().isUnauthorized());
        semToken.perform(get(carrinho).header("Authorization", "Bearer token-falso"))
                .andExpect(status().isUnauthorized());
        semToken.perform(get(carrinho).with(TokenDaRota.comoConta(tokens, contaFeirante)))
                .andExpect(status().isForbidden());
        semToken.perform(get(carrinho).with(TokenDaRota.comoConta(tokens, contaConsumidor)))
                .andExpect(status().isOk());
        // Vitrine e categorias continuam públicas.
        semToken.perform(get("/api/xepas")).andExpect(status().isOk());
        semToken.perform(get("/api/financeiro/categorias")).andExpect(status().isOk());
    }

    @Test
    void fotoFicaNoBancoEApareceNaXepaENoPerfil() throws Exception {
        semToken.perform(post("/api/imagens").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataUrl\":\"" + PNG + "\"}"))
                .andExpect(status().isUnauthorized());

        String enviada = semToken.perform(post("/api/imagens").with(TokenDaRota.comoConta(tokens, contaFeirante))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dataUrl\":\"" + PNG + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(enviada, "$.url");

        byte[] esperado = Base64.getDecoder().decode(PNG.substring(PNG.indexOf(',') + 1));
        semToken.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(esperado));

        mvc.perform(post("/api/feirante/" + contaFeirante + "/xepas").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Xepa com foto","categoria":"Frutas","preco":5,"quantidade":2,
                                 "idFeira":%d,"imagem":"%s"}""".formatted(idFeira, url)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imagem").value(url));

        mvc.perform(put("/api/conta/" + contaFeirante + "/perfil").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"foto":"%s","nascimento":"1980-05-10","matricula":"M-123","validade":"12/2027",
                                 "titular":"Fabiane Souza","barraca":"Barraca Nova","grupo":"Frutas"}""".formatted(url)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/conta/" + contaFeirante + "/perfil"))
                .andExpect(jsonPath("$.tipo").value("FEIRANTE"))
                .andExpect(jsonPath("$.foto").value(url))
                .andExpect(jsonPath("$.nascimento").value("1980-05-10"))
                .andExpect(jsonPath("$.validade").value("12/2027"))
                .andExpect(jsonPath("$.titular").value("Fabiane Souza"))
                .andExpect(jsonPath("$.barraca").value("Barraca Nova"));

        mvc.perform(put("/api/conta/" + contaFeirante + "/perfil").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"foto\":\"" + PNG + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chatDoPedidoEntreConsumidorEFeirante() throws Exception {
        String xepa = mvc.perform(post("/api/feirante/" + contaFeirante + "/xepas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Xepa de verduras\",\"categoria\":\"Verduras e legumes\","
                                + "\"preco\":10,\"quantidade\":3,\"idFeira\":" + idFeira + "}"))
                .andReturn().getResponse().getContentAsString();
        Integer idXepa = JsonPath.read(xepa, "$.id");

        // Dúvida antes da compra: reaproveitada se o consumidor clicar de novo.
        String consumidor = "/api/consumidor/" + contaConsumidor + "/conversas";
        String duvida = mvc.perform(post(consumidor).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idXepa\":" + idXepa + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pedido").doesNotExist())
                .andExpect(jsonPath("$.xepa.nome").value("Xepa de verduras"))
                .andReturn().getResponse().getContentAsString();
        Integer idDuvida = JsonPath.read(duvida, "$.id");
        mvc.perform(post(consumidor).contentType(MediaType.APPLICATION_JSON).content("{\"idXepa\":" + idXepa + "}"))
                .andExpect(jsonPath("$.id").value(idDuvida));

        // A compra abre a conversa do pedido.
        String pedidos = mvc.perform(post("/api/consumidor/" + contaConsumidor + "/pedidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itens\":[{\"idXepa\":" + idXepa + ",\"quantidade\":1}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].idConversa", notNullValue()))
                .andReturn().getResponse().getContentAsString();
        Integer idChat = JsonPath.read(pedidos, "$[0].idConversa");

        String feirante = "/api/feirante/" + contaFeirante + "/conversas";
        mvc.perform(get(feirante))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(idChat))
                .andExpect(jsonPath("$[0].mensagens[0].autor").value("sistema"))
                .andExpect(jsonPath("$[0].mensagens[0].texto", startsWith("Novo pedido nº")))
                .andExpect(jsonPath("$[0].leituras.feirante").doesNotExist());

        mvc.perform(post(consumidor + "/" + idChat + "/mensagens").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"Posso retirar às 12h?\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post(feirante + "/" + idChat + "/mensagens").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"Pode sim!\"}"))
                .andExpect(jsonPath("$.mensagens[1].autor").value("consumidor"))
                .andExpect(jsonPath("$.mensagens[2].autor").value("feirante"))
                .andExpect(jsonPath("$.leituras.feirante", notNullValue()));

        // Outra conta não vê a conversa.
        mvc.perform(get("/api/feirante/" + contaFeirante + "/conversas/" + idChat)).andExpect(status().isOk());
        mvc.perform(post(consumidor + "/" + idChat + "/finalizar")).andExpect(status().isForbidden());

        // "Pedido retirado": conversa finalizada e pedido retirado; não aceita mais mensagens.
        mvc.perform(post(feirante + "/" + idChat + "/finalizar"))
                .andExpect(jsonPath("$.status").value("finalizada"))
                .andExpect(jsonPath("$.pedido.status").value("Retirado"));
        mvc.perform(post(consumidor + "/" + idChat + "/mensagens").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"oi\"}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/consumidor/" + contaConsumidor + "/pedidos"))
                .andExpect(jsonPath("$[0].status").value("Retirado"));
    }
}
