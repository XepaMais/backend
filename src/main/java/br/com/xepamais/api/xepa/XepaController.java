package br.com.xepamais.api.xepa;

import br.com.xepamais.api.xepa.dto.XepaDtos.XepaRequest;
import br.com.xepamais.api.xepa.dto.XepaDtos.XepaResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@io.swagger.v3.oas.annotations.tags.Tag(name = "Xepas", description = "Catálogo de ofertas e xepas do feirante")
@RequestMapping("/api")
public class XepaController {
    private final XepaService service;

    public XepaController(XepaService service) {
        this.service = service;
    }

    // Xepas à venda: com estoque e não vencidas
    @GetMapping("/xepas")
    public List<XepaResponse> disponiveis(@RequestParam(required = false) String categoria,
                                          @RequestParam(required = false) Integer idFeira,
                                          @RequestParam(required = false) String busca) {
        return service.listarDisponiveis(categoria, idFeira, busca);
    }

    @GetMapping("/xepas/categorias")
    public List<String> categorias() {
        return Categorias.DO_APP;
    }

    @GetMapping("/xepas/{id}")
    public XepaResponse detalhe(@PathVariable Integer id) {
        return service.buscar(id);
    }

    @GetMapping("/feirante/{idConta}/xepas")
    public List<XepaResponse> doFeirante(@PathVariable Integer idConta) {
        return service.listarDoFeirante(idConta);
    }

    @PostMapping("/feirante/{idConta}/xepas")
    @ResponseStatus(HttpStatus.CREATED)
    public XepaResponse publicar(@PathVariable Integer idConta, @RequestBody XepaRequest request) {
        return service.publicar(idConta, request);
    }

    // Corrige a xepa: só os campos enviados mudam
    @PutMapping("/feirante/{idConta}/xepas/{id}")
    public XepaResponse editar(@PathVariable Integer idConta, @PathVariable Integer id,
                               @RequestBody XepaRequest request) {
        return service.editar(idConta, id, request);
    }

    // Exclui a xepa
    @DeleteMapping("/feirante/{idConta}/xepas/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelar(@PathVariable Integer idConta, @PathVariable Integer id) {
        service.cancelar(idConta, id);
    }
}
