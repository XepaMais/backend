package br.com.xepamais.api.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Rotas com {idConta} só respondem ao dono da conta
@Configuration
public class ContaInterceptor implements HandlerInterceptor, WebMvcConfigurer {
    public static final String ATRIBUTO_CONTA = "idContaLogada";
    public static final String ATRIBUTO_SESSAO = "idSessaoLogada";
    private static final Pattern ROTA_DA_CONTA =
            Pattern.compile("^/api/(?:consumidor|feirante|financeiro|conta|feiras/feirante)/(\\d+)(?:/.*)?$");

    private final SessaoService sessoes;

    public ContaInterceptor(SessaoService sessoes) {
        this.sessoes = sessoes;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this)
                .addPathPatterns("/api/consumidor/**", "/api/feirante/**", "/api/financeiro/**",
                        "/api/conta/**", "/api/feiras/feirante/**", "/api/imagens");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        String caminho = request.getRequestURI().substring(request.getContextPath().length());
        Matcher rota = ROTA_DA_CONTA.matcher(caminho);
        boolean envioDeFoto = caminho.equals("/api/imagens");
        // Rotas sem conta no caminho continuam abertas
        if (!rota.matches() && !envioDeFoto) return true;

        String cabecalho = request.getHeader("Authorization");
        String token = null;
        if (cabecalho != null && cabecalho.startsWith("Bearer ")) {
            token = cabecalho.substring(7);
        }
        // Token assinado pela API e sessão ainda ativa
        Optional<ContaTokenService.DadosToken> logada = sessoes.validar(token);
        if (logada.isEmpty()) {
            return negar(response, HttpServletResponse.SC_UNAUTHORIZED, "Sessão expirada. Entre novamente.");
        }
        if (rota.matches() && !rota.group(1).equals(String.valueOf(logada.get().idConta()))) {
            return negar(response, HttpServletResponse.SC_FORBIDDEN, "Você não tem acesso a esta conta.");
        }
        request.setAttribute(ATRIBUTO_CONTA, logada.get().idConta());
        request.setAttribute(ATRIBUTO_SESSAO, logada.get().idSessao());
        return true;
    }

    private boolean negar(HttpServletResponse response, int status, String mensagem) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"" + mensagem + "\"}");
        return false;
    }
}
