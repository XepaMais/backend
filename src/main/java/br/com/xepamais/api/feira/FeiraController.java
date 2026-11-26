package br.com.xepamais.api.feira;

import br.com.xepamais.api.feira.dto.FeiraDtos.FeiraResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Feiras: usadas pelo mapa do consumidor (feiras próximas).
@RestController
@RequestMapping("/api/feiras")
public class FeiraController {
    private final FeiraService service;

    public FeiraController(FeiraService service) {
        this.service = service;
    }

    // GET /api/feiras → todas as feiras
    @GetMapping
    public List<FeiraResponse> listar() {
        return service.listar();
    }

    // GET /api/feiras/proximas?lat=-23.52&lng=-46.69&raioKm=10 → feiras perto do usuário
    @GetMapping("/proximas")
    public List<FeiraResponse> listarProximas(@RequestParam double lat,
                                              @RequestParam double lng,
                                              @RequestParam(defaultValue = "10") double raioKm) {
        return service.listarProximas(lat, lng, raioKm);
    }
}
