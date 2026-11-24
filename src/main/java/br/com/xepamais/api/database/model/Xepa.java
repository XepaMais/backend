package br.com.xepamais.api.database.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "tb_xepa")
@Getter
@Setter
public class Xepa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_xepa")
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "id_produto", nullable = false)
    private Produto produto;

    @ManyToOne
    @JoinColumn(name = "id_feira", nullable = false)
    private Feira feira;

    @Column(name = "quantidade_xepa", nullable = false, precision = 10, scale = 2)
    private BigDecimal quantidade;

    @Column(name = "preco_xepa", nullable = false, precision = 10, scale = 2)
    private BigDecimal preco;

    @Column(name = "validade_xepa")
    private LocalDate validade;

    @Column(name = "data_retirada", nullable = false)
    private LocalDate dataRetirada;

    @Column(name = "horario_retirada_inicio", nullable = false)
    private LocalTime horarioRetiradaInicio;

    @Column(name = "horario_retirada_fim", nullable = false)
    private LocalTime horarioRetiradaFim;

    @Column(name = "status_xepa", nullable = false, length = 20)
    private String status;

    @Column(name = "data_cadastro", insertable = false, updatable = false)
    private LocalDateTime dataCadastro;

    @Version
    private Integer versao;
}
