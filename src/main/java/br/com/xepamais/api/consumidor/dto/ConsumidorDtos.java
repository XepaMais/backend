package br.com.xepamais.api.consumidor.dto;

import br.com.xepamais.api.xepa.dto.XepaDtos.XepaResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class ConsumidorDtos {
    private ConsumidorDtos() {}

    // ---------- Perfil ----------

    public record PerfilResponse(Integer id, String nome, String email, String cpf, String telefone,
                                 LocalDateTime dataCadastro) {}

    // Campos nulos ficam como estão. O CPF não muda depois do cadastro.
    public record PerfilRequest(String nome, String email, String telefone) {}

    public record SenhaRequest(String senhaAtual, String novaSenha) {}

    // ---------- Carrinho ----------

    // id = id da xepa (o front usa o id da xepa como id do item).
    public record ItemCarrinhoResponse(Integer id, BigDecimal quantidade, BigDecimal disponivel, XepaResponse xepa) {}

    // Itens que esgotaram, venceram ou saíram do ar não aparecem (e são tirados do carrinho).
    public record CarrinhoResponse(List<ItemCarrinhoResponse> itens, BigDecimal totalItens, BigDecimal total) {}

    public record ItemCarrinhoRequest(Integer idXepa, BigDecimal quantidade) {}

    public record QuantidadeRequest(BigDecimal quantidade) {}

    // Troca o carrinho inteiro (usado para sincronizar o carrinho que o navegador já tinha).
    public record CarrinhoRequest(List<ItemCarrinhoRequest> itens) {}

    // ---------- Pedidos ----------

    public record EnderecoRequest(String cep, String logradouro, String numero, String complemento,
                                  String bairro, String cidade, String uf) {}

    // Frete de cada feira, calculado no carrinho (servicos/frete.js).
    public record FreteFeiraRequest(String feira, BigDecimal valor) {}

    // tipo: "retirada" (na feira) ou "entrega" (em casa).
    public record RecebimentoRequest(String tipo, EnderecoRequest endereco, List<FreteFeiraRequest> fretes) {}

    // itens vazio = usa o carrinho salvo na API. metodoPagamento: PIX (padrão).
    public record CheckoutRequest(List<ItemCarrinhoRequest> itens, RecebimentoRequest recebimento,
                                  String metodoPagamento) {}

    public record ItemPedidoResponse(Integer id, Integer idFeirante, String nome, String categoria,
                                     String descricao, String imagem, String barraca, String feira,
                                     String retirada, BigDecimal quantidade, BigDecimal preco) {}

    public record FreteResponse(BigDecimal valor) {}

    // tipo: "retirada" ou "entrega" (mesmos valores do front).
    public record RecebimentoResponse(String tipo, String endereco, FreteResponse frete) {}

    // status: texto que o front mostra ("Pendente de retirada", "Retirado", "Cancelado").
    // situacao: valor salvo no banco (PAGO, PRONTO_RETIRADA, RETIRADO...).
    public record PedidoResponse(Integer id, String numero, LocalDateTime data, String status, String situacao,
                                 String codigo, BigDecimal subtotal, BigDecimal taxaEntrega, BigDecimal total,
                                 Integer idFeirante, String barraca, String consumidor,
                                 RecebimentoResponse recebimento, List<ItemPedidoResponse> itens) {}

    public record RetiradaRequest(String codigo) {}

    // ---------- Impacto ----------

    public record SemanaImpacto(Integer id, String nome, LocalDate inicio, BigDecimal kg) {}

    public record ImpactoResponse(BigDecimal kgSalvos, BigDecimal economia, BigDecimal xepasResgatadas,
                                  Integer refeicoes, Integer pedidos, List<SemanaImpacto> semanas) {}
}
