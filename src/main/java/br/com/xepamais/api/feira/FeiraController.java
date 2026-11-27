package br.com.xepamais.api.feira;

import br.com.xepamais.api.feira.dto.FeiraDtos.FeiraRequest;
import br.com.xepamais.api.feira.dto.FeiraDtos.FeiraResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

// Feiras
@RestController
@io.swagger.v3.oas.annotations.tags.Tag(name = "Feiras", description = "Mapa de feiras e feiras do feirante")
@RequestMapping("/api/feiras")
public class FeiraController {
    private final FeiraService service;

    public FeiraController(FeiraService service) {
        this.service = service;
    }

    // Lista as feiras, com busca opcional pelo nome
    @GetMapping
    public List<FeiraResponse> listar(@RequestParam(required = false) String busca) {
        if (busca == null || busca.isBlank()) {
            return service.listar();
        }
        return service.buscarPorNome(busca);
    }

    // GET /api/feiras/proximas?lat=-23.52&lng=-46.69&raioKm=10 → feiras perto do usuário
    @GetMapping("/proximas")
    public List<FeiraResponse> listarProximas(@RequestParam double lat,
                                              @RequestParam double lng,
                                              @RequestParam(defaultValue = "10") double raioKm) {
        return service.listarProximas(lat, lng, raioKm);
    }

    // Feiras do feirante

    @GetMapping("/feirante/{idConta}")
    public List<FeiraResponse> listarDoFeirante(@PathVariable Integer idConta) {
        return service.listarDoFeirante(idConta);
    }

    @PostMapping("/feirante/{idConta}")
    @ResponseStatus(HttpStatus.CREATED)
    public FeiraResponse criar(@PathVariable Integer idConta, @RequestBody FeiraRequest request) {
        return service.criar(idConta, request);
    }

    @PutMapping("/feirante/{idConta}/{idFeira}")
    public FeiraResponse atualizar(@PathVariable Integer idConta, @PathVariable Integer idFeira,
                                   @RequestBody FeiraRequest request) {
        return service.atualizar(idConta, idFeira, request);
    }

    @DeleteMapping("/feirante/{idConta}/{idFeira}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Integer idConta, @PathVariable Integer idFeira) {
        try {
            service.excluir(idConta, idFeira);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Esta feira tem xepas ou entregas registradas e não pode ser excluída.");
        }
    }
}
