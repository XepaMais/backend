package br.com.xepamais.api.database.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Table(name = "tb_item_pedido", uniqueConstraints = @UniqueConstraint(columnNames = {"id_pedido", "id_xepa"}))
@Getter
@Setter
public class ItemPedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_item_pedido")
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "id_pedido", nullable = false)
    private Pedido pedido;

    @ManyToOne
    @JoinColumn(name = "id_xepa", nullable = false)
    private Xepa xepa;

    @Column(name = "quantidade_item", nullable = false, precision = 10, scale = 2)
    private BigDecimal quantidade;

    @Column(name = "preco_unitario", nullable = false, precision = 10, scale = 2)
    private BigDecimal precoUnitario;
}
