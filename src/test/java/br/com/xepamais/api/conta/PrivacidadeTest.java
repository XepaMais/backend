package br.com.xepamais.api.conta;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// LGPD (aceite obrigatório, exportar e excluir dados), limite de login, formato de erro e Swagger.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:privacidade;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
@AutoConfigureMockMvc
class PrivacidadeTest {

    @Autowired MockMvc mvc;

    private static String cpf() {
        return String.valueOf(Math.abs(UUID.randomUUID().getMostSignificantBits())).substring(0, 11);
    }

    private String cadastro(String email, Boolean aceite) {
        return """
                {"nome":"Ana Souza","cpf":"%s","email":"%s","senha":"senha-forte-1","telefone":"11999990000"%s}
                """.formatted(cpf(), email, aceite == null ? "" : ",\"aceiteTermos\":" + aceite + ",\"aceiteMarketing\":true");
    }

    @Test
    void cadastroSemAceiteDosTermosERecusadoComErroPadronizado() throws Exception {
        mvc.perform(post("/api/auth/cadastro-consumidor").contentType(MediaType.APPLICATION_JSON)
                        .content(cadastro(UUID.randomUUID() + "@teste.com", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.erro").value("Bad Request"))
                .andExpect(jsonPath("$.message", containsString("Termos de Uso")))
                .andExpect(jsonPath("$.caminho").value("/api/auth/cadastro-consumidor"))
                .andExpect(jsonPath("$.momento").exists());
    }

    @Test
    void cadastroGuardaConsentimentoEExclusaoAnonimizaAConta() throws Exception {
        String email = UUID.randomUUID() + "@teste.com";
        String resposta = mvc.perform(post("/api/auth/cadastro-consumidor").contentType(MediaType.APPLICATION_JSON)
                        .content(cadastro(email, true)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(resposta, "$.id");
        String token = "Bearer " + JsonPath.read(resposta, "$.token");

        mvc.perform(get("/api/conta/" + id + "/consentimento").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versaoTermos").value("1.0"))
                .andExpect(jsonPath("$.aceiteTermosEm").exists())
                .andExpect(jsonPath("$.aceiteMarketing").value(true));

        mvc.perform(put("/api/conta/" + id + "/consentimento").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"aceiteMarketing\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aceiteMarketing").value(false));

        mvc.perform(get("/api/conta/" + id + "/dados").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("meus-dados-xepamais.json")))
                .andExpect(jsonPath("$.perfil.email").value(email))
                .andExpect(jsonPath("$.perfil.tipo").value("CONSUMIDOR"))
                .andExpect(jsonPath("$.pedidos").isArray())
                .andExpect(jsonPath("$.dispositivos", hasSize(1)));

        // Outra conta não consegue baixar estes dados.
        mvc.perform(get("/api/conta/" + (id + 1000) + "/dados").header("Authorization", token))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/conta/" + id).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"senha\":\"errada\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Senha incorreta."));

        mvc.perform(delete("/api/conta/" + id).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"senha\":\"senha-forte-1\"}"))
                .andExpect(status().isNoContent());

        // O login antigo deixa de valer e o e-mail não entra mais.
        mvc.perform(get("/api/conta/" + id + "/perfil").header("Authorization", token))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"senha-forte-1\"}"))
                .andExpect(status().isUnauthorized());
        // O e-mail fica livre para um cadastro novo.
        mvc.perform(post("/api/auth/cadastro-consumidor").contentType(MediaType.APPLICATION_JSON)
                        .content(cadastro(email, true)))
                .andExpect(status().isCreated());
    }

    @Test
    void loginBloqueiaDepoisDeCincoSenhasErradas() throws Exception {
        String email = UUID.randomUUID() + "@teste.com";
        mvc.perform(post("/api/auth/cadastro-consumidor").contentType(MediaType.APPLICATION_JSON)
                        .content(cadastro(email, true)))
                .andExpect(status().isCreated());
        String errado = "{\"email\":\"" + email + "\",\"senha\":\"senha-errada\"}";
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(errado))
                    .andExpect(status().isUnauthorized());
        }
        // Até a senha certa é recusada durante o bloqueio.
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"senha-forte-1\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message", containsString("15 minutos")));
    }

    @Test
    void jsonInvalidoERotaInexistenteUsamOFormatoPadrao() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{quebrado"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("formato inválido")));
        mvc.perform(get("/api/rota-que-nao-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Recurso não encontrado."));
    }

    @Test
    void documentacaoOpenApiEstaDisponivel() throws Exception {
        mvc.perform(get("/api/docs/openapi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Xepa+ API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/conta/{idConta}/dados']").exists());
    }
}
