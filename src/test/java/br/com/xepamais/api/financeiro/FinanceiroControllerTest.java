package br.com.xepamais.api.financeiro;

import br.com.xepamais.api.database.model.CategoriaDespesa;
import br.com.xepamais.api.database.model.Conta;
import br.com.xepamais.api.database.model.Feirante;
import br.com.xepamais.api.database.model.OrigemRenda;
import br.com.xepamais.api.database.repository.CategoriaDespesaRepository;
import br.com.xepamais.api.database.repository.ContaRepository;
import br.com.xepamais.api.database.repository.FeiranteRepository;
import br.com.xepamais.api.database.repository.OrigemRendaRepository;
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

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Testa o financeiro de ponta a ponta (HTTP -> serviço -> banco) com um banco H2 em memória.
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:financeiro;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
@AutoConfigureMockMvc
class FinanceiroControllerTest {

    private static final String HOJE = LocalDate.now(ZoneId.of("America/Sao_Paulo")).toString();

    @Autowired WebApplicationContext contexto;
    @Autowired SessaoService tokens;
    MockMvc mvc;
    @Autowired ContaRepository contaRepository;
    @Autowired FeiranteRepository feiranteRepository;
    @Autowired OrigemRendaRepository origemRendaRepository;
    @Autowired CategoriaDespesaRepository categoriaDespesaRepository;

    private String base;

    @BeforeEach
    void prepararFeirante() {
        mvc = TokenDaRota.mockMvc(contexto, tokens);
        if (origemRendaRepository.count() == 0) {
            for (String nome : List.of("Venda na feira", "Venda de xepa pelo Xepa+", "Encomenda",
                    "Entrega em domicílio", "Venda para comércio", "Outros")) {
                OrigemRenda o = new OrigemRenda();
                o.setNome(nome);
                origemRendaRepository.save(o);
            }
            for (String nome : List.of("Mercadoria", "Transporte", "Taxa do Xepa+", "Outras despesas")) {
                CategoriaDespesa c = new CategoriaDespesa();
                c.setNome(nome);
                categoriaDespesaRepository.save(c);
            }
        }
        Conta conta = new Conta();
        conta.setNome("Feirante Teste");
        conta.setEmail(UUID.randomUUID() + "@teste.com");
        conta.setSenha("x");
        conta = contaRepository.save(conta);
        Feirante feirante = new Feirante();
        feirante.setConta(conta);
        feirante.setNomeFantasia("Barraca Teste");
        feiranteRepository.save(feirante);
        base = "/api/financeiro/" + conta.getId();
    }

    private String renda(String origem, String descricao, String valor) {
        return """
                {"origem":"%s","descricao":"%s","valor":%s,"data":"%s"}""".formatted(origem, descricao, valor, HOJE);
    }

    @Test
    void categorias() throws Exception {
        mvc.perform(get("/api/financeiro/categorias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origensRenda", hasItem("Venda de xepa pelo Xepa+")))
                .andExpect(jsonPath("$.categoriasDespesa", hasItem("Mercadoria")));
    }

    @Test
    void fluxoCompleto_rendasDespesasResumoSaqueHistoricoPlanilha() throws Exception {
        // Rendas
        mvc.perform(post(base + "/rendas").contentType(MediaType.APPLICATION_JSON)
                        .content(renda("Venda de xepa pelo Xepa+", "Pedido app", "100.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.valor").value(100.00));
        String criada = mvc.perform(post(base + "/rendas").contentType(MediaType.APPLICATION_JSON)
                        .content(renda("Venda na feira", "Venda de tomate", "50")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String idRenda = criada.replaceAll(".*\"id\":(\\d+).*", "$1");

        // Editar renda manual
        mvc.perform(put(base + "/rendas/" + idRenda).contentType(MediaType.APPLICATION_JSON)
                        .content(renda("Venda na feira", "Venda de tomate e alface", "60")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.descricao").value("Venda de tomate e alface"))
                .andExpect(jsonPath("$.valor").value(60.00));

        // Despesa: 2 x 12,50 = 25,00
        String despesa = mvc.perform(post(base + "/despesas").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoria":"Mercadoria","descricao":"Caixa de tomate","quantidade":2,"valorUnitario":12.5,"data":"%s"}"""
                                .formatted(HOJE)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.valor").value(25.00))
                .andReturn().getResponse().getContentAsString();
        String idDespesa = despesa.replaceAll(".*\"id\":(\\d+).*", "$1");

        mvc.perform(get(base + "/rendas")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get(base + "/despesas")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));

        // Resumo: rendas 160, despesas 25, lucro 135 (hoje, semana e mês)
        mvc.perform(get(base + "/resumo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rendas.hoje").value(160.00))
                .andExpect(jsonPath("$.despesas.semana").value(25.00))
                .andExpect(jsonPath("$.lucro.mes").value(135.00));

        // Saldo para saque = só vendas pelo Xepa+ (100)
        mvc.perform(get(base + "/saldo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recebido").value(100.00))
                .andExpect(jsonPath("$.disponivel").value(100.00));

        mvc.perform(post(base + "/saques").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\":30,\"tipoChave\":\"email\",\"chave\":\"feirante@teste.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SOLICITADO"));
        mvc.perform(get(base + "/saldo")).andExpect(jsonPath("$.disponivel").value(70.00));
        mvc.perform(get(base + "/saques")).andExpect(jsonPath("$", hasSize(1)));

        // Saque maior que o saldo é recusado
        mvc.perform(post(base + "/saques").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\":80,\"tipoChave\":\"cpf\",\"chave\":\"123.456.789-09\"}"))
                .andExpect(status().isUnprocessableContent());

        // Histórico com filtros
        mvc.perform(get(base + "/historico"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registros", hasSize(3)))
                .andExpect(jsonPath("$.lucros").value(160.00))
                .andExpect(jsonPath("$.gastos").value(25.00))
                .andExpect(jsonPath("$.saldo").value(135.00));
        mvc.perform(get(base + "/historico").param("tipo", "gasto"))
                .andExpect(jsonPath("$.registros", hasSize(1)));
        mvc.perform(get(base + "/historico").param("busca", "tomate"))
                .andExpect(jsonPath("$.registros", hasSize(2)));

        // Planilha CSV
        mvc.perform(get(base + "/planilha"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("historico-financeiro.csv")))
                .andExpect(content().string(containsString("Caixa de tomate")));

        // Excluir despesa
        mvc.perform(delete(base + "/despesas/" + idDespesa)).andExpect(status().isNoContent());
        mvc.perform(get(base + "/despesas")).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(delete(base + "/despesas/" + idDespesa)).andExpect(status().isNotFound());
    }

    @Test
    void validacoes() throws Exception {
        mvc.perform(post(base + "/rendas").contentType(MediaType.APPLICATION_JSON)
                        .content(renda("Venda na feira", "Zero", "0")))
                .andExpect(status().isBadRequest());
        mvc.perform(post(base + "/rendas").contentType(MediaType.APPLICATION_JSON)
                        .content(renda("Origem que não existe", "X", "10")))
                .andExpect(status().isBadRequest());
        mvc.perform(post(base + "/despesas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoria\":\"Mercadoria\",\"descricao\":\"\",\"quantidade\":1,\"valorUnitario\":5}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(base + "/saques").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"valor\":10,\"tipoChave\":\"cpf\",\"chave\":\"123\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(base + "/historico").param("tipo", "outro")).andExpect(status().isBadRequest());
        // Com o token desta conta, o financeiro de outra conta é recusado antes de chegar ao banco.
        Integer idConta = Integer.valueOf(base.substring(base.lastIndexOf('/') + 1));
        mvc.perform(get("/api/financeiro/999999/resumo")
                        .header("Authorization", "Bearer " + tokens.abrir(idConta, "teste")))
                .andExpect(status().isForbidden());
    }
}
