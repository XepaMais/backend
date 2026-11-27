package br.com.xepamais.api.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// Bloqueia /api/admin/** para quem não manda um token válido
@Configuration
public class AdminInterceptor implements HandlerInterceptor, WebMvcConfigurer {
    private final AdminTokenService tokens;

    public AdminInterceptor(AdminTokenService tokens) {
        this.tokens = tokens;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this)
                .addPathPatterns("/api/admin/**")
                .excludePathPatterns("/api/admin/login");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        String cabecalho = request.getHeader("Authorization");
        String token = cabecalho != null && cabecalho.startsWith("Bearer ") ? cabecalho.substring(7) : null;
        if (tokens.valido(token)) return true;

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"Sessão expirada. Entre novamente.\"}");
        return false;
    }
}
