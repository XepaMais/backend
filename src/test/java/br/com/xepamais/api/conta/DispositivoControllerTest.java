package br.com.xepamais.api.conta;

import br.com.xepamais.api.auth.PasswordHasher;
import br.com.xepamais.api.database.model.Conta;
import br.com.xepamais.api.database.model.Consumidor;
import br.com.xepamais.api.database.repository.ConsumidorRepository;
import br.com.xepamais.api.database.repository.ContaRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
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

// Dispositivos conectados: cada login é um aparelho; desconectar derruba o login daquele aparelho.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:dispositivos;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
@AutoConfigureMockMvc
class DispositivoControllerTest {

    private static final String CELULAR =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36";
    private static final String COMPUTADOR =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0 Safari/537.36 Edg/120.0";

    @Autowired MockMvc mvc;
    @Autowired PasswordHasher senhas;
    @Autowired ContaRepository contaRepository;
    @Autowired ConsumidorRepository consumidorRepository;

    Conta conta;

    @BeforeEach
    void preparar() {
        conta = new Conta();
        conta.setNome("Henrique");
        conta.setEmail(UUID.randomUUID() + "@teste.com");
        conta.setSenha(senhas.hash("senha1234"));
        conta = contaRepository.save(conta);
        Consumidor consumidor = new Consumidor();
        consumidor.setConta(conta);
        consumidorRepository.save(consumidor);
    }

    private String entrar(String navegador) throws Exception {
        String resposta = mvc.perform(post("/api/auth/login").header("User-Agent", navegador)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + conta.getEmail() + "\",\"senha\":\"senha1234\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(resposta, "$.token");
    }

    @Test
    void listaDesconectaESai() throws Exception {
        String celular = entrar(CELULAR);
        String computador = entrar(COMPUTADOR);
        String dispositivos = "/api/conta/" + conta.getId() + "/dispositivos";

        String lista = mvc.perform(get(dispositivos).header("Authorization", computador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.atual == true)].nome", contains("Windows")))
                .andExpect(jsonPath("$[?(@.atual == true)].navegador", contains("Microsoft Edge")))
                .andExpect(jsonPath("$[?(@.atual == false)].nome", contains("Android")))
                .andExpect(jsonPath("$[?(@.atual == false)].tipo", contains("celular")))
                .andReturn().getResponse().getContentAsString();
        Integer idCelular = ((net.minidev.json.JSONArray) JsonPath.read(lista, "$[?(@.atual == false)].id"))
                .stream().map(id -> (Integer) id).findFirst().orElseThrow();

        // Desconectar o celular: o login dele para de valer na hora.
        mvc.perform(delete(dispositivos + "/" + idCelular).header("Authorization", computador))
                .andExpect(status().isNoContent());
        mvc.perform(get(dispositivos).header("Authorization", celular))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(dispositivos).header("Authorization", computador))
                .andExpect(jsonPath("$", hasSize(1)));

        // "Sair dos outros aparelhos" mantém este.
        String outro = entrar(CELULAR);
        mvc.perform(delete(dispositivos).header("Authorization", computador))
                .andExpect(jsonPath("$.desconectados", greaterThanOrEqualTo(1)));
        mvc.perform(get(dispositivos).header("Authorization", outro)).andExpect(status().isUnauthorized());
        mvc.perform(get(dispositivos).header("Authorization", computador)).andExpect(status().isOk());

        // Sair: o token deste aparelho também deixa de valer.
        mvc.perform(post("/api/conta/" + conta.getId() + "/sair").header("Authorization", computador))
                .andExpect(status().isNoContent());
        mvc.perform(get(dispositivos).header("Authorization", computador))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void naoDesconectaAparelhoDeOutraConta() throws Exception {
        String meu = entrar(COMPUTADOR);
        Conta outra = new Conta();
        outra.setNome("Outra");
        outra.setEmail(UUID.randomUUID() + "@teste.com");
        outra.setSenha(senhas.hash("senha1234"));
        outra = contaRepository.save(outra);
        mvc.perform(get("/api/conta/" + outra.getId() + "/dispositivos").header("Authorization", meu))
                .andExpect(status().isForbidden());
    }
}
