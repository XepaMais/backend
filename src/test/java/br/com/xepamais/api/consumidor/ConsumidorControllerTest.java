package br.com.xepamais.api.consumidor;

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
import br.com.xepamais.api.TokenDaRota;
import br.com.xepamais.api.auth.SessaoService;

import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Área do consumidor de ponta a ponta (HTTP -> serviço -> banco) com H2 em memória:
// feirante publica xepa -> consumidor vê, põe no carrinho, compra -> histórico, impacto, venda e retirada.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:consumidor;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
@AutoConfigureMockMvc
class ConsumidorControllerTest {

    @Autowired WebApplicationContext contexto;
    @Autowired SessaoService tokens;
    MockMvc mvc;
    @Autowired ContaRepository contaRepository;
    @Autowired FeiranteRepository feiranteRepository;
    @Autowired ConsumidorRepository consumidorRepository;
    @Autowired FeiraRepository feiraRepository;

    private Integer contaFeirante;
    private Integer contaConsumidor;
    private Integer idFeira;

    @BeforeEach
    void preparar() {
        mvc = TokenDaRota.mockMvc(contexto, tokens);
        Feira feira = new Feira();
        feira.setNome("Feira " + UUID.randomUUID().toString().substring(0, 6));
        feira.setEndereco("Rua Clélia");
        feira.setNumero("100");
        feira.setCidade("São Paulo");
        feira.setUf("SP");
        feira.setHorarioInicio(LocalTime.of(7, 0));
        feira.setHorarioFim(LocalTime.of(13, 0));
        feira = feiraRepository.save(feira);
        idFeira = feira.getId();

        Feirante feirante = new Feirante();
        feirante.setConta(conta("Feirante"));
        feirante.setNomeFantasia("Barraca da Fabiane");
        feirante.setFeiras(new HashSet<>(Set.of(feira)));
        feiranteRepository.save(feirante);
        contaFeirante = feirante.getConta().getId();

        Consumidor consumidor = new Consumidor();
        consumidor.setConta(conta("Consumidor"));
        consumidorRepository.save(consumidor);
        contaConsumidor = consumidor.getConta().getId();
    }

    private Conta conta(String nome) {
        Conta conta = new Conta();
        conta.setNome(nome + " Teste");
        conta.setEmail(UUID.randomUUID() + "@teste.com");
        conta.setSenha("x");
        conta.setTelefone("11999999999");
        return contaRepository.save(conta);
    }

    private Integer publicarXepa(String nome, String categoria, String preco, int quantidade) throws Exception {
        String corpo = """
                {"nome":"%s","categoria":"%s","itens":"Tomate, alface","descricao":"Fresquinha",
                 "preco":%s,"quantidade":%d,"idFeira":%d}""".formatted(nome, categoria, preco, quantidade, idFeira);
        String resposta = mvc.perform(post("/api/feirante/" + contaFeirante + "/xepas")
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoria").value(categoria))
                .andExpect(jsonPath("$.itens").value("Tomate, alface"))
                .andExpect(jsonPath("$.descricao").value("Fresquinha"))
                .andExpect(jsonPath("$.horario").value("07:00 às 13:00"))
                .andExpect(jsonPath("$.barraca").value("Barraca da Fabiane"))
                .andExpect(jsonPath("$.idFeirante").value(contaFeirante))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(resposta, "$.id");
    }

    @Test
    void fluxoCompletoDeCompra() throws Exception {
        Integer verduras = publicarXepa("Xepa de verduras", "Verduras e legumes", "10.00", 5);
        Integer frutas = publicarXepa("Xepa de frutas", "Frutas", "8.50", 2);

        // Vitrine com filtros.
        mvc.perform(get("/api/xepas").param("categoria", "Frutas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(frutas)))
                .andExpect(jsonPath("$[*].id", not(hasItem(verduras))));
        mvc.perform(get("/api/xepas").param("busca", "VERDURA"))
                .andExpect(jsonPath("$[*].id", hasItem(verduras)));
        mvc.perform(get("/api/xepas/" + frutas)).andExpect(jsonPath("$.preco").value(8.5));

        // Carrinho: quantidade acima do estoque fica limitada ao disponível.
        String carrinho = "/api/consumidor/" + contaConsumidor + "/carrinho";
        mvc.perform(put(carrinho + "/itens/" + verduras).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantidade\":2}"))
                .andExpect(status().isOk());
        mvc.perform(put(carrinho + "/itens/" + frutas).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantidade\":9}"))
                .andExpect(jsonPath("$.itens", hasSize(2)))
                .andExpect(jsonPath("$.itens[1].quantidade").value(2))
                .andExpect(jsonPath("$.total").value(37.0));

        // Checkout com entrega: usa o carrinho salvo.
        String checkout = """
                {"recebimento":{"tipo":"entrega",
                  "endereco":{"cep":"05042-000","logradouro":"Rua A","numero":"10","bairro":"Lapa",
                              "cidade":"São Paulo","uf":"sp"},
                  "fretes":[{"feira":"%s","valor":6.79}]}}""".formatted(
                feiraRepository.findById(idFeira).orElseThrow().getNome());
        String base = "/api/consumidor/" + contaConsumidor;
        mvc.perform(post(base + "/pedidos").contentType(MediaType.APPLICATION_JSON).content(checkout))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("Pendente de retirada"))
                .andExpect(jsonPath("$[0].subtotal").value(37.0))
                .andExpect(jsonPath("$[0].taxaEntrega").value(6.79))
                .andExpect(jsonPath("$[0].total").value(43.79))
                .andExpect(jsonPath("$[0].codigo", matchesPattern("\\d{4}")))
                .andExpect(jsonPath("$[0].data", notNullValue()))
                .andExpect(jsonPath("$[0].recebimento.tipo").value("entrega"))
                .andExpect(jsonPath("$[0].recebimento.endereco", containsString("Lapa, São Paulo/SP")))
                .andExpect(jsonPath("$[0].itens", hasSize(2)));

        // Estoque baixou; frutas esgotaram e saíram da vitrine. Carrinho ficou vazio.
        mvc.perform(get("/api/xepas/" + verduras)).andExpect(jsonPath("$.quantidade").value(3));
        mvc.perform(get("/api/xepas")).andExpect(jsonPath("$[*].id", not(hasItem(frutas))));
        mvc.perform(get(carrinho)).andExpect(jsonPath("$.itens", hasSize(0)));

        // Histórico e impacto: 2 verduras (2 kg cada) + 2 frutas (2 kg cada) = 8 kg = 16 refeições.
        String historico = mvc.perform(get(base + "/pedidos"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andReturn().getResponse().getContentAsString();
        Integer idPedido = JsonPath.read(historico, "$[0].id");
        String codigo = JsonPath.read(historico, "$[0].codigo");
        mvc.perform(get(base + "/impacto"))
                .andExpect(jsonPath("$.kgSalvos").value(8.0))
                .andExpect(jsonPath("$.refeicoes").value(16))
                .andExpect(jsonPath("$.xepasResgatadas").value(4))
                .andExpect(jsonPath("$.semanas", hasSize(6)))
                .andExpect(jsonPath("$.semanas[5].kg").value(8.0));

        // Lado do feirante: vê a venda, a renda caiu no financeiro e confirma a retirada pelo código.
        String vendas = "/api/feirante/" + contaFeirante + "/pedidos";
        mvc.perform(get(vendas))
                .andExpect(jsonPath("$[0].id").value(idPedido))
                .andExpect(jsonPath("$[0].codigo").doesNotExist());
        // Sem plano escolhido vale o Semente (15% sobre os 37,00 das xepas = 5,55): 43,79 - 5,55.
        mvc.perform(get("/api/financeiro/" + contaFeirante + "/rendas"))
                .andExpect(jsonPath("$[0].valor").value(38.24));
        mvc.perform(post(vendas + "/" + idPedido + "/retirada").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"0000\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(vendas + "/" + idPedido + "/retirada").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"" + codigo + "\"}"))
                .andExpect(jsonPath("$.status").value("Retirado"));
    }

    @Test
    void naoVendeMaisQueOEstoque() throws Exception {
        Integer xepa = publicarXepa("Xepa completa", "Completa", "15.00", 1);
        mvc.perform(post("/api/consumidor/" + contaConsumidor + "/pedidos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itens\":[{\"idXepa\":" + xepa + ",\"quantidade\":3}]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Só restam 1")));
    }

    @Test
    void carrinhoVazioNaoFinalizaCompra() throws Exception {
        mvc.perform(post("/api/consumidor/" + contaConsumidor + "/pedidos"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void perfilDoConsumidor() throws Exception {
        String perfil = "/api/conta/" + contaConsumidor + "/perfil";
        mvc.perform(put(perfil).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Henrique Mazza\",\"telefone\":\"11988887777\"}"))
                .andExpect(status().isOk());
        mvc.perform(get(perfil))
                .andExpect(jsonPath("$.nome").value("Henrique Mazza"))
                .andExpect(jsonPath("$.telefone").value("11988887777"));
        mvc.perform(put(perfil).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"invalido\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void feiranteNaoPublicaEmFeiraDeOutro() throws Exception {
        Feira outra = new Feira();
        outra.setNome("Outra feira");
        outra.setEndereco("Rua B");
        outra.setCidade("São Paulo");
        outra.setUf("SP");
        outra = feiraRepository.save(outra);
        mvc.perform(post("/api/feirante/" + contaFeirante + "/xepas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"X\",\"categoria\":\"Frutas\",\"preco\":5,\"quantidade\":1,\"idFeira\":"
                                + outra.getId() + "}"))
                .andExpect(status().isBadRequest());
    }
}
