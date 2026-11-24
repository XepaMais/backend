package br.com.xepamais.api.auth;

import br.com.xepamais.api.auth.dto.CadastroConsumidorRequest;
import br.com.xepamais.api.auth.dto.CadastroFeiranteRequest;
import br.com.xepamais.api.auth.dto.ContaResponse;
import br.com.xepamais.api.auth.dto.LoginRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/cadastro-consumidor")
    @ResponseStatus(HttpStatus.CREATED)
    public ContaResponse cadastrarConsumidor(@RequestBody CadastroConsumidorRequest request) {
        return authService.cadastrarConsumidor(request);
    }

    @PostMapping("/cadastro-feirante")
    @ResponseStatus(HttpStatus.CREATED)
    public ContaResponse cadastrarFeirante(@RequestBody CadastroFeiranteRequest request) {
        return authService.cadastrarFeirante(request);
    }

    @PostMapping("/login")
    public ContaResponse login(@RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
