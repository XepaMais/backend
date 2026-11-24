package br.com.xepamais.api.database.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalTime;

@Entity
@Table(name = "tb_feira")
@Getter
@Setter
public class Feira {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_feira")
    private Integer id;

    @Column(name = "nome_feira", nullable = false, length = 100)
    private String nome;

    @Column(name = "endereco_feira", nullable = false, length = 150)
    private String endereco;

    @Column(name = "cep_feira", length = 8)
    private String cep;

    @Column(name = "cidade_feira", nullable = false, length = 80)
    private String cidade;

    @Column(name = "uf_feira", nullable = false, length = 2)
    private String uf;

    @Column(name = "dia_semana", nullable = false)
    private Integer diaSemana;

    @Column(name = "horario_inicio")
    private LocalTime horarioInicio;

    @Column(name = "horario_fim")
    private LocalTime horarioFim;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;
}
