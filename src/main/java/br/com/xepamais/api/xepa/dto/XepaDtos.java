package br.com.xepamais.api.xepa.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class XepaDtos {
    private XepaDtos() {}

    // Mesmos nomes de campo que o front-end já usa nas xepas (cards, carrinho, detalhe).
    // idFeirante = id da CONTA do feirante (igual ao "id" do login).
    public record XepaResponse(Integer id, String nome, String categoria, String itens, String descricao,
                               String imagem, String unidade, BigDecimal preco, BigDecimal precoReferencia,
                               BigDecimal quantidade, LocalDate validade, LocalDate dataRetirada,
                               LocalTime horarioRetiradaInicio, LocalTime horarioRetiradaFim, String horario,
                               String status, Integer idFeira, String feira, String local, String cep,
                               List<Integer> dias, BigDecimal latitude, BigDecimal longitude,
                               Integer idFeirante, String feirante, String barraca,
                               java.time.LocalDateTime dataCadastro) {}

    // Cadastro da xepa pelo feirante. categoria: "Verduras e legumes", "Frutas", "Tempero" ou "Completa".
    // itens = o que vem na xepa; imagem = endereço (URL) da foto, opcional.
    public record XepaRequest(String nome, String categoria, String itens, String descricao, String imagem,
                              String unidade, BigDecimal preco, BigDecimal precoReferencia, BigDecimal quantidade,
                              LocalDate validade, LocalDate dataRetirada, LocalTime horarioRetiradaInicio,
                              LocalTime horarioRetiradaFim, Integer idFeira) {}
}
