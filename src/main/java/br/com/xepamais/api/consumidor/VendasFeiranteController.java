package br.com.xepamais.api.consumidor;

import br.com.xepamais.api.consumidor.dto.ConsumidorDtos.PedidoResponse;
import br.com.xepamais.api.consumidor.dto.ConsumidorDtos.RetiradaRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// O outro lado dos pedidos do consumidor: o feirante vê as vendas e confirma a retirada.
@RestController
@io.swagger.v3.oas.annotations.tags.Tag(name = "Vendas do feirante", description = "Pedidos recebidos, cancelamento e confirmação de retirada")
@RequestMapping("/api/feirante/{idConta}/pedidos")
public class VendasFeiranteController {
    private final PedidoService pedidoService;

    public VendasFeiranteController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @GetMapping
    public List<PedidoResponse> vendas(@PathVariable Integer idConta) {
        return pedidoService.vendasDoFeirante(idConta);
    }

    // O feirante cancela a venda (ex.: a xepa acabou na barraca).
    @PostMapping("/{idPedido}/cancelar")
    public PedidoResponse cancelar(@PathVariable Integer idConta, @PathVariable Integer idPedido) {
        return pedidoService.cancelarPeloFeirante(idConta, idPedido);
    }

    // { "codigo": "1234" } — o código que o consumidor mostra na retirada.
    @PostMapping("/{idPedido}/retirada")
    public PedidoResponse confirmarRetirada(@PathVariable Integer idConta, @PathVariable Integer idPedido,
                                            @RequestBody RetiradaRequest request) {
        return pedidoService.confirmarRetirada(idConta, idPedido, request);
    }
}
