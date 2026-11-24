package br.com.xepamais.api.database.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Table(name = "tb_item_carrinho", uniqueConstraints = @UniqueConstraint(columnNames = {"id_carrinho", "id_xepa"}))
@Getter
@Setter
public class ItemCarrinho {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_item_carrinho")
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "id_carrinho", nullable = false)
    private Carrinho carrinho;

    @ManyToOne
    @JoinColumn(name = "id_xepa", nullable = false)
    private Xepa xepa;

    @Column(name = "quantidade_item", nullable = false, precision = 10, scale = 2)
    private BigDecimal quantidade;
}
