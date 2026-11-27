package br.com.xepamais.api.auth;

import br.com.xepamais.api.auth.dto.CadastroConsumidorRequest;
import br.com.xepamais.api.auth.dto.CadastroFeiranteRequest;
import br.com.xepamais.api.auth.dto.ContaResponse;
import br.com.xepamais.api.auth.dto.LoginRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@io.swagger.v3.oas.annotations.tags.Tag(name = "Autenticação", description = "Cadastro de consumidor e feirante e login (devolve o token)")
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/cadastro-consumidor")
    @ResponseStatus(HttpStatus.CREATED)
    public ContaResponse cadastrarConsumidor(@RequestBody CadastroConsumidorRequest request,
                                             @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        return authService.cadastrarConsumidor(request, userAgent);
    }

    @PostMapping("/cadastro-feirante")
    @ResponseStatus(HttpStatus.CREATED)
    public ContaResponse cadastrarFeirante(@RequestBody CadastroFeiranteRequest request,
                                           @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        return authService.cadastrarFeirante(request, userAgent);
    }

    @PostMapping("/login")
    public ContaResponse login(@RequestBody LoginRequest request,
                               @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        return authService.login(request, userAgent);
    }

    // { "perfil": "consumidor" | "gratis" | "crescer" | "pro" }: entra numa conta da demonstração.
    public record DemoRequest(String perfil) {}

    @PostMapping("/demo")
    public ContaResponse demo(@RequestBody DemoRequest request,
                              @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        return authService.entrarNaDemo(request == null ? null : request.perfil(), userAgent);
    }
}
