package br.com.xepamais.api.database.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// Lucro do feirante
@Entity
@Table(name = "tb_renda")
@Getter
@Setter
public class Renda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_renda")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_feirante", nullable = false)
    private Feirante feirante;

    @ManyToOne
    @JoinColumn(name = "id_origem_renda", nullable = false)
    private OrigemRenda origem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_pedido")
    private Pedido pedido;

    @ManyToOne
    @JoinColumn(name = "id_feira")
    private Feira feira;

    @Column(name = "descricao_renda", nullable = false, length = 150)
    private String descricao;

    @Column(name = "valor_renda", nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Column(name = "data_renda", nullable = false)
    private LocalDate data;

    @Column(name = "data_cadastro", insertable = false, updatable = false)
    private LocalDateTime dataCadastro;
}
