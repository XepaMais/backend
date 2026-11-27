package br.com.xepamais.api.avaliacao;

import br.com.xepamais.api.avaliacao.AvaliacaoService.PerfilPublicoResponse;
import br.com.xepamais.api.consumidor.dto.ConsumidorDtos.AvaliacaoRequest;
import br.com.xepamais.api.consumidor.dto.ConsumidorDtos.PedidoResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@io.swagger.v3.oas.annotations.tags.Tag(name = "Avaliações", description = "Avaliação de pedidos e perfil público do feirante")
@RequestMapping("/api")
public class AvaliacaoController {
    private final AvaliacaoService service;

    public AvaliacaoController(AvaliacaoService service) {
        this.service = service;
    }

    // { "nota": 5, "comentario": "Tudo fresquinho!" } — só depois que o pedido foi retirado/entregue.
    @PutMapping("/consumidor/{idConta}/pedidos/{idPedido}/avaliacao")
    public PedidoResponse avaliar(@PathVariable Integer idConta, @PathVariable Integer idPedido,
                                  @RequestBody AvaliacaoRequest request) {
        return service.avaliar(idConta, idPedido, request);
    }

    // Público: perfil do feirante na página da xepa (foto, barraca, média e avaliações recentes).
    @GetMapping("/feirantes/{idContaFeirante}")
    public PerfilPublicoResponse perfilPublico(@PathVariable Integer idContaFeirante) {
        return service.perfilPublico(idContaFeirante);
    }
}
