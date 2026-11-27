package br.com.xepamais.api.consumidor.dto;

import br.com.xepamais.api.xepa.dto.XepaDtos.XepaResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class ConsumidorDtos {
    private ConsumidorDtos() {}

    // Carrinho

    // id = id da xepa
    public record ItemCarrinhoResponse(Integer id, BigDecimal quantidade, BigDecimal disponivel, XepaResponse xepa) {}

    // Itens que esgotaram, venceram ou saíram do ar não aparecem
    public record CarrinhoResponse(List<ItemCarrinhoResponse> itens, BigDecimal totalItens, BigDecimal total) {}

    public record ItemCarrinhoRequest(Integer idXepa, BigDecimal quantidade) {}

    public record QuantidadeRequest(BigDecimal quantidade) {}

    // Troca o carrinho inteiro
    public record CarrinhoRequest(List<ItemCarrinhoRequest> itens) {}

    // Endereço de entrega

    // tipo: "retirada" ou "entrega"
    public record EnderecoResponse(String cep, String logradouro, String numero, String complemento,
                                   String bairro, String cidade, String uf) {}

    public record RecebimentoPreferidoResponse(String tipo, EnderecoResponse endereco) {}

    // Pode salvar o endereço pela metade
    public record RecebimentoPreferidoRequest(String tipo, EnderecoRequest endereco) {}

    // Pedidos

    public record EnderecoRequest(String cep, String logradouro, String numero, String complemento,
                                  String bairro, String cidade, String uf) {}

    // Frete de cada feira, calculado no carrinho
    public record FreteFeiraRequest(String feira, BigDecimal valor) {}

    // tipo: "retirada" ou "entrega"
    public record RecebimentoRequest(String tipo, EnderecoRequest endereco, List<FreteFeiraRequest> fretes) {}

    // itens vazio = usa o carrinho salvo na API. metodoPagamento: PIX
    public record CheckoutRequest(List<ItemCarrinhoRequest> itens, RecebimentoRequest recebimento,
                                  String metodoPagamento) {}

    public record ItemPedidoResponse(Integer id, Integer idFeirante, String nome, String categoria,
                                     String descricao, String imagem, String barraca, String feira,
                                     String retirada, BigDecimal quantidade, BigDecimal preco) {}

    public record FreteResponse(BigDecimal valor) {}

    // tipo: "retirada" ou "entrega"
    public record RecebimentoResponse(String tipo, String endereco, FreteResponse frete) {}

    // status
    public record PedidoResponse(Integer id, String numero, LocalDateTime data, String status, String situacao,
                                 String codigo, BigDecimal subtotal, BigDecimal taxaEntrega, BigDecimal total,
                                 Integer idFeirante, String barraca, String consumidor,
                                 RecebimentoResponse recebimento, List<ItemPedidoResponse> itens,
                                 Integer idConversa, AvaliacaoResponse avaliacao) {
        // Para o feirante: o código fica só com o consumidor
        public PedidoResponse semCodigo() {
            return new PedidoResponse(id, numero, data, status, situacao, null, subtotal, taxaEntrega, total,
                    idFeirante, barraca, consumidor, recebimento, itens, idConversa, avaliacao);
        }
    }

    // Avaliação do pedido pelo consumidor. nota: 1 a 5
    public record AvaliacaoResponse(Integer nota, String comentario, LocalDateTime data) {}

    public record AvaliacaoRequest(Integer nota, String comentario) {}

    public record RetiradaRequest(String codigo) {}

    // Impacto

    public record SemanaImpacto(Integer id, String nome, LocalDate inicio, BigDecimal kg) {}

    public record ImpactoResponse(BigDecimal kgSalvos, BigDecimal economia, BigDecimal xepasResgatadas,
                                  Integer refeicoes, Integer pedidos, List<SemanaImpacto> semanas) {}
}
