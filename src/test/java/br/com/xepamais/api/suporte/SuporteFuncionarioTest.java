package br.com.xepamais.api.suporte;

import br.com.xepamais.api.TokenDaRota;
import br.com.xepamais.api.auth.SessaoService;
import br.com.xepamais.api.database.model.Conta;
import br.com.xepamais.api.database.model.Feirante;
import br.com.xepamais.api.database.repository.ContaRepository;
import br.com.xepamais.api.database.repository.FeiranteRepository;
import br.com.xepamais.api.database.repository.MensagemSuporteRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Mensagens do Suporte no banco e funcionários das feiras do feirante.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:suporte;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
class SuporteFuncionarioTest {

    @Autowired WebApplicationContext contexto;
    @Autowired SessaoService tokens;
    @Autowired ContaRepository contaRepository;
    @Autowired FeiranteRepository feiranteRepository;
    @Autowired MensagemSuporteRepository suporteRepository;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    MockMvc mvc;
    Integer contaFeirante;

    @BeforeEach
    void preparar() {
        mvc = TokenDaRota.mockMvc(contexto, tokens);
        // Mesma regra do banco de produção (xepaDB): funcionário só existe para um vínculo feirante–feira.
        jdbc.execute("ALTER TABLE tb_funcionario_feira ADD CONSTRAINT IF NOT EXISTS fk_funcionario_feirante_feira "
                + "FOREIGN KEY (id_feirante, id_feira) REFERENCES tb_feirante_feira(id_feirante, id_feira)");
        Conta conta = new Conta();
        conta.setNome("Fabiane");
        conta.setEmail(UUID.randomUUID() + "@teste.com");
        conta.setSenha("x");
        conta = contaRepository.save(conta);
        Feirante feirante = new Feirante();
        feirante.setConta(conta);
        feirante.setNomeFantasia("Barraca");
        feiranteRepository.save(feirante);
        contaFeirante = conta.getId();
    }

    private static RequestPostProcessor ip(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private static String mensagem(String email) {
        return """
                {"nome":"Henrique","email":"%s","perfil":"consumidor","assunto":"Pedido","mensagem":"Não recebi o código."}"""
                .formatted(email);
    }

    @Test
    @Transactional
    void mensagemDeSuporteFicaSalva() throws Exception {
        String resposta = mvc.perform(post("/api/suporte").with(ip("10.1.1.1")).contentType(MediaType.APPLICATION_JSON)
                        .content(mensagem("henrique@teste.com")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.protocolo", notNullValue()))
                .andReturn().getResponse().getContentAsString();
        Integer protocolo = JsonPath.read(resposta, "$.protocolo");
        assertEquals("Não recebi o código.", suporteRepository.findById(protocolo).orElseThrow().getMensagem());
        assertEquals("ABERTA", suporteRepository.findById(protocolo).orElseThrow().getStatus());

        mvc.perform(post("/api/suporte").with(ip("10.1.1.1")).contentType(MediaType.APPLICATION_JSON)
                        .content(mensagem("email-invalido")))
                .andExpect(status().isBadRequest());

        // Logado: a mensagem fica ligada à conta.
        String logado = mvc.perform(post("/api/suporte").with(ip("10.1.1.2"))
                        .header("Authorization", "Bearer " + tokens.abrir(contaFeirante, "teste"))
                        .contentType(MediaType.APPLICATION_JSON).content(mensagem("fabi@teste.com")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Integer protocoloLogado = JsonPath.read(logado, "$.protocolo");
        assertEquals(contaFeirante, suporteRepository.findById(protocoloLogado).orElseThrow().getConta().getId());
    }

    @Test
    void suporteTemLimiteContraSpam() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/suporte").with(ip("10.9.9.9")).contentType(MediaType.APPLICATION_JSON)
                    .content(mensagem("a@b.com"))).andExpect(status().isCreated());
        }
        mvc.perform(post("/api/suporte").with(ip("10.9.9.9")).contentType(MediaType.APPLICATION_JSON)
                        .content(mensagem("a@b.com")))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void funcionariosDasFeirasFicamNoBanco() throws Exception {
        String feiras = "/api/feiras/feirante/" + contaFeirante;
        String criada = mvc.perform(post(feiras).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Feira Funcionarios %s","endereco":"Rua A","cidade":"São Paulo","uf":"SP",
                                 "latitude":-23.5,"longitude":-46.6,"funcionarios":[" Ana ","Bia","Ana",""]}"""
                                .formatted(UUID.randomUUID().toString().substring(0, 5))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.funcionarios", contains("Ana", "Bia")))
                .andReturn().getResponse().getContentAsString();
        Integer idFeira = JsonPath.read(criada, "$.id");
        String nome = JsonPath.read(criada, "$.nome");

        // Editar sem mandar a lista mantém os funcionários.
        String semLista = """
                {"nome":"%s","endereco":"Rua B","cidade":"São Paulo","uf":"SP","latitude":-23.5,"longitude":-46.6}"""
                .formatted(nome);
        mvc.perform(put(feiras + "/" + idFeira).contentType(MediaType.APPLICATION_JSON).content(semLista))
                .andExpect(jsonPath("$.funcionarios", contains("Ana", "Bia")));
        mvc.perform(put(feiras + "/" + idFeira).contentType(MediaType.APPLICATION_JSON)
                        .content(semLista.replace("}", ",\"funcionarios\":[\"Carlos\"]}")))
                .andExpect(jsonPath("$.funcionarios", contains("Carlos")));

        mvc.perform(get(feiras)).andExpect(jsonPath("$[0].funcionarios", contains("Carlos")));
        // Mapa público não lista funcionários; o perfil público do feirante mostra por feira.
        mvc.perform(get("/api/feiras").param("busca", nome)).andExpect(jsonPath("$[0].funcionarios", hasSize(0)));
        mvc.perform(get("/api/feirantes/" + contaFeirante))
                .andExpect(jsonPath("$.feiras[0].idFeira").value(idFeira))
                .andExpect(jsonPath("$.feiras[0].funcionarios", contains("Carlos")));

        mvc.perform(delete(feiras + "/" + idFeira)).andExpect(status().isNoContent());
        mvc.perform(get(feiras)).andExpect(jsonPath("$", hasSize(0)));
    }
}
