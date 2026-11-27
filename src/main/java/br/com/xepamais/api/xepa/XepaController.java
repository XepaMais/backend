package br.com.xepamais.api.xepa;

import br.com.xepamais.api.xepa.dto.XepaDtos.XepaRequest;
import br.com.xepamais.api.xepa.dto.XepaDtos.XepaResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Vitrine de xepas (consumidor) e publicação de xepas (feirante).
// {idConta} é o "id" devolvido pelo login.
@RestController
@RequestMapping("/api")
public class XepaController {
    private final XepaService service;

    public XepaController(XepaService service) {
        this.service = service;
    }

    // Xepas à venda: com estoque e não vencidas. Filtros opcionais: ?categoria=Frutas&idFeira=1&busca=tomate
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

    // Corrige a xepa: só os campos enviados mudam. Ex.: { "preco": 8.50, "quantidade": 3 }
    @PutMapping("/feirante/{idConta}/xepas/{id}")
    public XepaResponse editar(@PathVariable Integer idConta, @PathVariable Integer id,
                               @RequestBody XepaRequest request) {
        return service.editar(idConta, id, request);
    }

    // Exclui a xepa (sai da vitrine; pedidos antigos continuam mostrando o que foi comprado).
    @DeleteMapping("/feirante/{idConta}/xepas/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelar(@PathVariable Integer idConta, @PathVariable Integer id) {
        service.cancelar(idConta, id);
    }
}
