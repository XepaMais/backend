package br.com.xepamais.api.financeiro.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class FinanceiroDtos {
    private FinanceiroDtos() {}

    public record RendaRequest(String origem, String descricao, BigDecimal valor, LocalDate data) {}

    public record RendaResponse(Integer id, String tipo, String descricao, BigDecimal valor, LocalDate data,
                                String categoria, String feira, Integer pedido) {}

    public record DespesaRequest(String categoria, String descricao, BigDecimal quantidade,
                                 BigDecimal valorUnitario, LocalDate data) {}

    public record DespesaResponse(Integer id, String descricao, BigDecimal quantidade, BigDecimal valorUnitario,
                                  BigDecimal valor, String categoria, LocalDate data, String feira, Integer pedido) {}

    public record SaqueRequest(BigDecimal valor, String tipoChave, String chave) {}

    public record SaqueResponse(Integer id, BigDecimal valor, String tipoChave, String chave, String status,
                                LocalDateTime data) {}

    public record SaldoResponse(BigDecimal recebido, BigDecimal sacado, BigDecimal disponivel) {}

    public record Totais(BigDecimal hoje, BigDecimal semana, BigDecimal mes) {}

    // lucro = rendas - despesas em cada período
    public record ResumoResponse(Totais rendas, Totais despesas, Totais lucro) {}

    // Linha da planilha: tipo é "lucro" ou "gasto"
    public record RegistroHistorico(String id, LocalDate data, String descricao, String categoria, String tipo,
                                    BigDecimal valor, String feira) {}

    public record HistoricoResponse(List<RegistroHistorico> registros, BigDecimal lucros, BigDecimal gastos,
                                    BigDecimal saldo) {}
}
