package br.com.xepamais.api.feira.dto;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

public final class FeiraDtos {
    private FeiraDtos() {}

    // dias: 1=segunda ... 7=domingo
    public record FeiraResponse(Integer id, String nome, String endereco, String numero, String cep, String cidade,
                                String uf, List<Integer> dias, LocalTime horarioInicio, LocalTime horarioFim,
                                BigDecimal latitude, BigDecimal longitude) {}

    // Cadastro/edição da feira pelo feirante. Horários no formato "07:00"; dias: 1=segunda ... 7=domingo.
    public record FeiraRequest(String nome, String endereco, String numero, String cep, String cidade, String uf,
                               List<Integer> dias, LocalTime horarioInicio, LocalTime horarioFim,
                               BigDecimal latitude, BigDecimal longitude) {}
}
