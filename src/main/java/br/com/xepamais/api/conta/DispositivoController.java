package br.com.xepamais.api.conta;

import br.com.xepamais.api.auth.ContaInterceptor;
import br.com.xepamais.api.auth.SessaoService;
import br.com.xepamais.api.auth.SessaoService.DispositivoResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// Dispositivos conectados e sair. {idConta} = id do login (a rota exige o token da mesma conta).
@RestController
@RequestMapping("/api/conta/{idConta}")
public class DispositivoController {
    private final SessaoService sessoes;

    public DispositivoController(SessaoService sessoes) {
        this.sessoes = sessoes;
    }

    // Aparelhos com login ativo. "atual": true = este aparelho.
    @GetMapping("/dispositivos")
    public List<DispositivoResponse> listar(@PathVariable Integer idConta,
                                            @RequestAttribute(ContaInterceptor.ATRIBUTO_SESSAO) Integer idSessao) {
        return sessoes.listar(idConta, idSessao);
    }

    // Desconecta um aparelho: o login dele deixa de valer na hora.
    @DeleteMapping("/dispositivos/{idDispositivo}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desconectar(@PathVariable Integer idConta, @PathVariable Integer idDispositivo) {
        sessoes.encerrar(idConta, idDispositivo);
    }

    // Desconecta todos os outros aparelhos (este continua logado).
    @DeleteMapping("/dispositivos")
    public Map<String, Integer> desconectarOutros(@PathVariable Integer idConta,
                                                  @RequestAttribute(ContaInterceptor.ATRIBUTO_SESSAO) Integer idSessao) {
        return Map.of("desconectados", sessoes.encerrarOutras(idConta, idSessao));
    }

    // Botão "Sair": encerra o login deste aparelho.
    @PostMapping("/sair")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sair(@PathVariable Integer idConta,
                     @RequestAttribute(ContaInterceptor.ATRIBUTO_SESSAO) Integer idSessao) {
        sessoes.encerrar(idConta, idSessao);
    }
}
