package br.com.xepamais.api.database.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "tb_despesa")
@Getter
@Setter
public class Despesa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_despesa")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_feirante", nullable = false)
    private Feirante feirante;

    @ManyToOne
    @JoinColumn(name = "id_categoria_despesa", nullable = false)
    private CategoriaDespesa categoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_pedido")
    private Pedido pedido;

    @ManyToOne
    @JoinColumn(name = "id_feira")
    private Feira feira;

    @Column(name = "descricao_despesa", nullable = false, length = 150)
    private String descricao;

    @Column(name = "quantidade_despesa", nullable = false, precision = 10, scale = 2)
    private BigDecimal quantidade;

    @Column(name = "valor_unitario", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorUnitario;

    // valor_despesa é calculado pelo MySQL, por isso não é gravado aqui
    @Column(name = "data_despesa", nullable = false)
    private LocalDate data;

    @Column(name = "data_cadastro", insertable = false, updatable = false)
    private LocalDateTime dataCadastro;

    // Mesmo cálculo da coluna valor_despesa do banco
    public BigDecimal valorTotal() {
        return quantidade.multiply(valorUnitario).setScale(2, RoundingMode.HALF_UP);
    }
}
