package br.com.xepamais.api.conta;

import br.com.xepamais.api.conta.PrivacidadeService.ConsentimentoRequest;
import br.com.xepamais.api.conta.PrivacidadeService.ConsentimentoResponse;
import br.com.xepamais.api.conta.PrivacidadeService.ExclusaoRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// LGPD: a pessoa consulta, baixa e apaga os próprios dados
@RestController
@RequestMapping("/api/conta/{idConta}")
@Tag(name = "Privacidade (LGPD)", description = "Direitos do titular: acesso, portabilidade, consentimento e exclusão")
public class PrivacidadeController {
    private final PrivacidadeService service;

    public PrivacidadeController(PrivacidadeService service) {
        this.service = service;
    }

    @GetMapping("/dados")
    @Operation(summary = "Baixa todos os dados pessoais da conta em JSON (acesso e portabilidade)")
    public ResponseEntity<Map<String, Object>> exportar(@PathVariable Integer idConta) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"meus-dados-xepamais.json\"")
                .body(service.exportar(idConta));
    }

    @GetMapping("/consentimento")
    @Operation(summary = "Mostra quando os termos foram aceitos e se a pessoa aceita receber novidades")
    public ConsentimentoResponse consentimento(@PathVariable Integer idConta) {
        return service.consentimento(idConta);
    }

    @PutMapping("/consentimento")
    @Operation(summary = "Dá ou retira o consentimento para receber novidades por e-mail")
    public ConsentimentoResponse alterarConsentimento(@PathVariable Integer idConta,
                                                      @RequestBody ConsentimentoRequest request) {
        return service.alterarConsentimento(idConta, request);
    }

    // Exclui a conta (precisa da senha)
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Exclui a conta: anonimiza os dados pessoais e encerra todos os logins")
    public void excluir(@PathVariable Integer idConta, @RequestBody ExclusaoRequest request) {
        service.excluir(idConta, request);
    }
}
