package br.com.xepamais.api.plano;

import br.com.xepamais.api.plano.PlanoService.EscolhaPlanoRequest;
import br.com.xepamais.api.plano.PlanoService.PlanoDoFeiranteResponse;
import br.com.xepamais.api.plano.PlanoService.PlanoResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class PlanoController {
    private final PlanoService service;

    public PlanoController(PlanoService service) {
        this.service = service;
    }

    // Pública: os planos aparecem também na Home.
    @GetMapping("/planos")
    public List<PlanoResponse> listar() {
        return service.listar();
    }

    @GetMapping("/feirante/{idConta}/plano")
    public PlanoDoFeiranteResponse doFeirante(@PathVariable Integer idConta) {
        return service.doFeirante(idConta);
    }

    // { "codigo": "crescer" }
    @PutMapping("/feirante/{idConta}/plano")
    public PlanoDoFeiranteResponse escolher(@PathVariable Integer idConta, @RequestBody EscolhaPlanoRequest request) {
        return service.escolher(idConta, request);
    }
}
