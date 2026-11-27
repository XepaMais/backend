package br.com.xepamais.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
    private final String[] allowedOrigins;

    public CorsConfig(@Value("${app.cors.allowed-origins:http://localhost:5173}") String origins) {
        // Os sites vêm separados por vírgula
        java.util.List<String> lista = new java.util.ArrayList<>();
        for (String site : origins.split(",")) {
            site = site.trim();
            if (!site.isEmpty()) {
                lista.add(site);
            }
        }
        this.allowedOrigins = lista.toArray(new String[0]);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "Authorization");
    }
}
