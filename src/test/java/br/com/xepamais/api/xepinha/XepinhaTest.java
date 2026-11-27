package br.com.xepamais.api.xepinha;

import br.com.xepamais.api.TokenDaRota;
import br.com.xepamais.api.auth.SessaoService;
import br.com.xepamais.api.database.model.Conta;
import br.com.xepamais.api.database.model.Feirante;
import br.com.xepamais.api.database.repository.ContaRepository;
import br.com.xepamais.api.database.repository.FeiranteRepository;
import br.com.xepamais.api.database.repository.PlanoRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Xepinha pela API: a IA é chamada pelo servidor (aqui, uma IA falsa) e as conversas ficam no banco.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:xepinha;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
class XepinhaTest {

    // Responde "Resposta N" e guarda o que recebeu; com falhar = true finge que a IA caiu.
    static class IaFalsa implements ClienteIa {
        final List<String> instrucoes = new ArrayList<>();
        final List<List<Turno>> historicos = new ArrayList<>();
        volatile boolean falhar;

        @Override
        public synchronized String responder(String instrucoes, List<Turno> historico, String pergunta) {
            if (falhar) throw new IaIndisponivelException("fora do ar", true, null);
            this.instrucoes.add(instrucoes);
            this.historicos.add(historico);
            return "Resposta " + historicos.size() + " para: " + pergunta;
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        IaFalsa iaFalsa() {
            return new IaFalsa();
        }
    }

    @Autowired WebApplicationContext contexto;
    @Autowired SessaoService tokens;
    @Autowired ContaRepository contaRepository;
    @Autowired FeiranteRepository feiranteRepository;
    @Autowired PlanoRepository planoRepository;
    @Autowired IaFalsa ia;

    MockMvc mvc;
    Feirante feirante;

    @BeforeEach
    void preparar() {
        mvc = TokenDaRota.mockMvc(contexto, tokens);
        ia.falhar = false;
        Conta conta = new Conta();
        conta.setNome("Fabiane");
        conta.setEmail(UUID.randomUUID() + "@teste.com");
        conta.setSenha("x");
        conta = contaRepository.save(conta);
        feirante = new Feirante();
        feirante.setConta(conta);
        feirante.setNomeFantasia("Barraca");
        feirante.setPlano(planoRepository.findByCodigo("crescer").orElseThrow());
        feiranteRepository.save(feirante);
    }

    private String base() {
        return "/api/feirante/" + feirante.getConta().getId() + "/xepinha";
    }

    @Test
    void conversaFicaSalvaEOHistoricoVaiParaAIa() throws Exception {
        String primeira = mvc.perform(post(base() + "/mensagens").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"Como registro uma despesa no Xepa+?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Como registro uma despesa no Xepa+?"))
                .andExpect(jsonPath("$.mensagens", hasSize(2)))
                .andExpect(jsonPath("$.mensagens[0].remetente").value("user"))
                .andExpect(jsonPath("$.mensagens[1].remetente").value("ia"))
                .andReturn().getResponse().getContentAsString();
        Integer idConversa = JsonPath.read(primeira, "$.id");

        // A persona e a base de conhecimento vão do servidor para a IA.
        assertTrue(ia.instrucoes.get(ia.instrucoes.size() - 1).contains("Você é a Xepinha"));
        assertTrue(ia.instrucoes.get(ia.instrucoes.size() - 1).contains("## BASE DE CONHECIMENTO"));

        mvc.perform(post(base() + "/mensagens").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idConversa\":" + idConversa + ",\"texto\":\"E uma venda?\"}"))
                .andExpect(jsonPath("$.mensagens", hasSize(4)));
        // A segunda pergunta levou a conversa anterior junto.
        List<ClienteIa.Turno> historico = ia.historicos.get(ia.historicos.size() - 1);
        assertEquals(2, historico.size());
        assertEquals("user", historico.get(0).papel());
        assertEquals("model", historico.get(1).papel());

        mvc.perform(get(base() + "/conversas"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].mensagens[3].texto", containsString("E uma venda?")));
    }

    @Test
    void falhaDaIaNaoSalvaNada() throws Exception {
        ia.falhar = true;
        mvc.perform(post(base() + "/mensagens").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"Oi\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(status().reason(XepinhaService.MSG_SOBRECARGA));
        mvc.perform(get(base() + "/conversas")).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void soPlanosBrotoEColheitaUsamAXepinha() throws Exception {
        feirante.setPlano(planoRepository.findByCodigo("gratis").orElseThrow());
        feiranteRepository.save(feirante);
        mvc.perform(post(base() + "/mensagens").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"Oi\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void demonstracaoSemLoginTemLimite() throws Exception {
        String corpo = "{\"historico\":[{\"remetente\":\"user\",\"texto\":\"Oi\"},{\"remetente\":\"ia\",\"texto\":\"Olá!\"}],"
                + "\"texto\":\"O que é xepa?\"}";
        mvc.perform(post("/api/xepinha/demo").with(r -> { r.setRemoteAddr("10.0.0.9"); return r; })
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resposta", containsString("O que é xepa?")));
        for (int i = 1; i < 15; i++) {
            mvc.perform(post("/api/xepinha/demo").with(r -> { r.setRemoteAddr("10.0.0.9"); return r; })
                    .contentType(MediaType.APPLICATION_JSON).content(corpo)).andExpect(status().isOk());
        }
        mvc.perform(post("/api/xepinha/demo").with(r -> { r.setRemoteAddr("10.0.0.9"); return r; })
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isTooManyRequests());
    }
}
