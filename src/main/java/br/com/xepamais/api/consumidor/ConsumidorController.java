package br.com.xepamais.api.consumidor;

import br.com.xepamais.api.consumidor.dto.ConsumidorDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Área do consumidor: perfil, carrinho, pedidos e impacto.
// {idConta} é o "id" devolvido pelo login (conta do tipo CONSUMIDOR).
@RestController
@RequestMapping("/api/consumidor/{idConta}")
public class ConsumidorController {
    private final ConsumidorService consumidorService;
    private final PedidoService pedidoService;

    public ConsumidorController(ConsumidorService consumidorService, PedidoService pedidoService) {
        this.consumidorService = consumidorService;
        this.pedidoService = pedidoService;
    }

    // ---------- Perfil ----------

    @GetMapping("/perfil")
    public PerfilResponse perfil(@PathVariable Integer idConta) {
        return consumidorService.perfil(idConta);
    }

    @PutMapping("/perfil")
    public PerfilResponse atualizarPerfil(@PathVariable Integer idConta, @RequestBody PerfilRequest request) {
        return consumidorService.atualizarPerfil(idConta, request);
    }

    @PutMapping("/senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void trocarSenha(@PathVariable Integer idConta, @RequestBody SenhaRequest request) {
        consumidorService.trocarSenha(idConta, request);
    }

    // ---------- Carrinho ----------

    @GetMapping("/carrinho")
    public CarrinhoResponse carrinho(@PathVariable Integer idConta) {
        return consumidorService.carrinho(idConta);
    }

    // Troca o carrinho inteiro: { "itens": [ { "idXepa": 1, "quantidade": 2 } ] }
    @PutMapping("/carrinho")
    public CarrinhoResponse substituirCarrinho(@PathVariable Integer idConta, @RequestBody CarrinhoRequest request) {
        return consumidorService.substituir(idConta, request);
    }

    // Adiciona a xepa ou troca a quantidade: { "quantidade": 2 }
    @PutMapping("/carrinho/itens/{idXepa}")
    public CarrinhoResponse definirItem(@PathVariable Integer idConta, @PathVariable Integer idXepa,
                                        @RequestBody QuantidadeRequest request) {
        return consumidorService.definirItem(idConta, idXepa, request == null ? null : request.quantidade());
    }

    @DeleteMapping("/carrinho/itens/{idXepa}")
    public CarrinhoResponse removerItem(@PathVariable Integer idConta, @PathVariable Integer idXepa) {
        return consumidorService.removerItem(idConta, idXepa);
    }

    @DeleteMapping("/carrinho")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void esvaziarCarrinho(@PathVariable Integer idConta) {
        consumidorService.esvaziar(idConta);
    }

    // ---------- Pedidos ----------

    // Confirma o pagamento. Devolve um pedido por feirante.
    @PostMapping("/pedidos")
    @ResponseStatus(HttpStatus.CREATED)
    public List<PedidoResponse> finalizarCompra(@PathVariable Integer idConta,
                                                @RequestBody(required = false) CheckoutRequest request) {
        return pedidoService.finalizarCompra(idConta, request);
    }

    @GetMapping("/pedidos")
    public List<PedidoResponse> pedidos(@PathVariable Integer idConta) {
        return pedidoService.listar(idConta);
    }

    @GetMapping("/pedidos/{idPedido}")
    public PedidoResponse pedido(@PathVariable Integer idConta, @PathVariable Integer idPedido) {
        return pedidoService.detalhe(idConta, idPedido);
    }

    // ---------- Impacto ----------

    @GetMapping("/impacto")
    public ImpactoResponse impacto(@PathVariable Integer idConta) {
        return pedidoService.impacto(idConta);
    }
}
