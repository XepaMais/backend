package br.com.xepamais.api.database.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "tb_gasto")
@Getter
@Setter
public class Gasto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_gasto")
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "id_feirante", nullable = false)
    private Feirante feirante;

    @ManyToOne
    @JoinColumn(name = "id_categoria_gasto", nullable = false)
    private CategoriaGasto categoriaGasto;

    @Column(name = "valor_gasto", nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Column(name = "data_gasto", nullable = false)
    private LocalDate data;

    @Column(name = "descricao_gasto", length = 150)
    private String descricao;
}
