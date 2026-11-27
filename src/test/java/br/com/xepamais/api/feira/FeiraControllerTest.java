package br.com.xepamais.api.feira;

import br.com.xepamais.api.TokenDaRota;
import br.com.xepamais.api.auth.ContaTokenService;
import br.com.xepamais.api.database.model.Conta;
import br.com.xepamais.api.database.model.Feirante;
import br.com.xepamais.api.database.repository.ContaRepository;
import br.com.xepamais.api.database.repository.FeiranteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Feira cadastrada pelo feirante aparece no mapa geral do consumidor e na busca pelo nome.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:feiras;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
class FeiraControllerTest {

    @Autowired WebApplicationContext contexto;
    @Autowired ContaTokenService tokens;
    @Autowired ContaRepository contaRepository;
    @Autowired FeiranteRepository feiranteRepository;

    MockMvc mvc;
    Integer contaFeirante;

    @BeforeEach
    void preparar() {
        mvc = TokenDaRota.mockMvc(contexto, tokens);
        Conta conta = new Conta();
        conta.setNome("Feirante");
        conta.setEmail(UUID.randomUUID() + "@teste.com");
        conta.setSenha("x");
        conta = contaRepository.save(conta);
        Feirante feirante = new Feirante();
        feirante.setConta(conta);
        feirante.setNomeFantasia("Barraca");
        feiranteRepository.save(feirante);
        contaFeirante = conta.getId();
    }

    private void criarFeira(String nome, double lat, double lng) throws Exception {
        mvc.perform(post("/api/feiras/feirante/" + contaFeirante).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"%s","endereco":"Rua A","numero":"10","cep":"05042-000","cidade":"São Paulo",
                                 "uf":"sp","dias":[2,6],"horarioInicio":"07:00","horarioFim":"13:00",
                                 "latitude":%s,"longitude":%s}""".formatted(nome, lat, lng)))
                .andExpect(status().isCreated());
    }

    @Test
    void feiraNovaApareceNoMapaGeralENaBusca() throws Exception {
        String sufixo = UUID.randomUUID().toString().substring(0, 5);
        criarFeira("Feira São João " + sufixo, -23.53, -46.69);
        // Bem longe (outra cidade): aparece no mapa geral mesmo assim.
        criarFeira("Feirinha do Porto " + sufixo, -22.90, -43.17);

        mvc.perform(get("/api/feiras"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nome", hasItems("Feira São João " + sufixo, "Feirinha do Porto " + sufixo)));

        // Sem acento e em maiúsculas: acha do mesmo jeito.
        mvc.perform(get("/api/feiras").param("busca", "SAO JOAO " + sufixo))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nome").value("Feira São João " + sufixo))
                .andExpect(jsonPath("$[0].uf").value("SP"))
                .andExpect(jsonPath("$[0].dias", contains(2, 6)));

        mvc.perform(get("/api/feiras").param("busca", "porto " + sufixo))
                .andExpect(jsonPath("$[*].nome", contains("Feirinha do Porto " + sufixo)));

        mvc.perform(get("/api/feiras").param("busca", "nao-existe-" + sufixo))
                .andExpect(jsonPath("$", hasSize(0)));

        // A feira também fica na lista do feirante (tela "Minhas feiras").
        mvc.perform(get("/api/feiras/feirante/" + contaFeirante))
                .andExpect(jsonPath("$", hasSize(2)));
    }
}
