package br.com.xepamais.api;

import br.com.xepamais.api.auth.ContaTokenService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

// Nos testes, cada requisição a /api/.../{idConta}/... vai com o token dessa conta (como o front faz).
public final class TokenDaRota {
    private static final Pattern ROTA =
            Pattern.compile("^/api/(?:consumidor|feirante|financeiro|conta|feiras/feirante)/(\\d+)(?:/.*)?$");

    private TokenDaRota() {}

    public static MockMvc mockMvc(WebApplicationContext contexto, ContaTokenService tokens) {
        return MockMvcBuilders.webAppContextSetup(contexto).defaultRequest(get("/").with(token(tokens))).build();
    }

    public static RequestPostProcessor token(ContaTokenService tokens) {
        return request -> {
            Matcher rota = ROTA.matcher(request.getRequestURI());
            if (rota.matches() && request.getHeader("Authorization") == null) {
                request.addHeader("Authorization", "Bearer " + tokens.gerar(Integer.valueOf(rota.group(1))));
            }
            return request;
        };
    }

    public static RequestPostProcessor comoConta(ContaTokenService tokens, Integer idConta) {
        return request -> {
            request.addHeader("Authorization", "Bearer " + tokens.gerar(idConta));
            return request;
        };
    }
}
