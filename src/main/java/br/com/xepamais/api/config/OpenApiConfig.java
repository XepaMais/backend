package br.com.xepamais.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

// Documentação interativa da API em /api/docs
@Configuration
public class OpenApiConfig {
    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI xepaMaisOpenApi() {
        String descricao = """
                API REST do Xepa+: conecta feirantes que têm sobras do fim da feira (xepas) a consumidores.

                **Autenticação:** `POST /api/auth/login` devolve um `token`. Envie-o nas rotas da conta \
                no cabeçalho `Authorization: Bearer <token>`.

                - Rotas com `{idConta}` só respondem ao dono da conta.
                - `401` = sem token, token inválido ou sessão encerrada (entre de novo).
                - `403` = o token é de outra conta.
                - `429` = muitas tentativas; aguarde alguns minutos.

                **Erros** sempre vêm no mesmo formato: \
                `{ "status", "erro", "message", "caminho", "momento" }`. O campo `message` é o texto \
                mostrado ao usuário.

                **Datas:** `AAAA-MM-DD`. **Valores:** número com ponto decimal (ex.: `12.50`).
                """;
        return new OpenAPI()
                .info(new Info()
                        .title("Xepa+ API")
                        .version("1.0")
                        .description(descricao)
                        .contact(new Contact().name("Equipe Xepa+").url("https://xepamais.com.br")))
                .servers(List.of(
                        new Server().url("https://xepamais.com.br").description("Produção"),
                        new Server().url("http://localhost:8080").description("Local")))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .description("Token devolvido por POST /api/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
