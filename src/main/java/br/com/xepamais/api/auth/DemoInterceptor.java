package br.com.xepamais.api.auth;

import br.com.xepamais.api.database.model.Conta;
import br.com.xepamais.api.database.repository.ContaRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// As contas da demonstração são usadas por todo mundo ao mesmo tempo
@Configuration
public class DemoInterceptor implements HandlerInterceptor, WebMvcConfigurer {
    private static final Pattern BLOQUEADAS = Pattern.compile(
            "^(?:PUT /api/conta/(\\d+)/senha"
                    + "|DELETE /api/conta/(\\d+)"
                    + "|DELETE /api/conta/(\\d+)/dispositivos(?:/\\d+)?"
                    + "|PUT /api/feirante/(\\d+)/plano)$");

    private final ContaRepository contaRepository;

    public DemoInterceptor(ContaRepository contaRepository) {
        this.contaRepository = contaRepository;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/conta/**", "/api/feirante/**").order(10);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String caminho = request.getRequestURI().substring(request.getContextPath().length());
        Matcher rota = BLOQUEADAS.matcher(request.getMethod().toUpperCase() + " " + caminho);
        if (!rota.matches()) return true;

        Integer idConta = null;
        for (int grupo = 1; grupo <= rota.groupCount() && idConta == null; grupo++) {
            if (rota.group(grupo) != null) idConta = Integer.valueOf(rota.group(grupo));
        }
        boolean demo = false;
        if (idConta != null) {
            Conta conta = contaRepository.findById(idConta).orElse(null);
            if (conta != null && ContaDemo.ehDemo(conta.getEmail())) {
                demo = true;
            }
        }
        if (!demo) return true;

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"Na demonstração essa ação fica bloqueada. "
                + "Crie sua conta grátis para usar todos os recursos.\"}");
        return false;
    }
}
