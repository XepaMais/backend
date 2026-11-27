package br.com.xepamais.api.feira.dto;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

public final class FeiraDtos {
    private FeiraDtos() {}

    // dias: 1=segunda ... 7=domingo. funcionarios: só nas rotas do feirante (no mapa público vem vazio).
    public record FeiraResponse(Integer id, String nome, String endereco, String numero, String cep, String cidade,
                                String uf, List<Integer> dias, LocalTime horarioInicio, LocalTime horarioFim,
                                BigDecimal latitude, BigDecimal longitude, List<String> funcionarios) {}

    // Cadastro/edição da feira pelo feirante. Horários no formato "07:00"; dias: 1=segunda ... 7=domingo.
    // funcionarios: quem atende na barraca do feirante nessa feira (null = não mexe).
    public record FeiraRequest(String nome, String endereco, String numero, String cep, String cidade, String uf,
                               List<Integer> dias, LocalTime horarioInicio, LocalTime horarioFim,
                               BigDecimal latitude, BigDecimal longitude, List<String> funcionarios) {}
}
