package br.com.xepamais.api;

import br.com.xepamais.api.auth.SessaoService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

// Nos testes, cada requisição a /api/.../{idConta}/... vai com o token dessa conta (como o front faz).
// O token vem de uma sessão de login de verdade (uma por conta, reaproveitada).
public final class TokenDaRota {
    private static final Pattern ROTA =
            Pattern.compile("^/api/(?:consumidor|feirante|financeiro|conta|feiras/feirante)/(\\d+)(?:/.*)?$");
    private static final String NAVEGADOR = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0 Safari/537.36";

    private TokenDaRota() {}

    public static MockMvc mockMvc(WebApplicationContext contexto, SessaoService sessoes) {
        Map<Integer, String> tokens = new ConcurrentHashMap<>();
        RequestPostProcessor token = request -> {
            Matcher rota = ROTA.matcher(request.getRequestURI());
            if (rota.matches() && request.getHeader("Authorization") == null) {
                Integer idConta = Integer.valueOf(rota.group(1));
                String valor = tokens.computeIfAbsent(idConta, id -> sessoes.abrir(id, NAVEGADOR));
                request.addHeader("Authorization", "Bearer " + valor);
            }
            return request;
        };
        return MockMvcBuilders.webAppContextSetup(contexto).defaultRequest(get("/").with(token)).build();
    }

    // Abre uma sessão nova da conta (use com um MockMvc sem token automático).
    public static RequestPostProcessor comoConta(SessaoService sessoes, Integer idConta) {
        String valor = sessoes.abrir(idConta, NAVEGADOR);
        return request -> {
            request.addHeader("Authorization", "Bearer " + valor);
            return request;
        };
    }
}
