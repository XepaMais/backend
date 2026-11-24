package br.com.xepamais.api.database.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tb_pedido")
@Getter
@Setter
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pedido")
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "id_consumidor", nullable = false)
    private Consumidor consumidor;

    @ManyToOne
    @JoinColumn(name = "id_feirante", nullable = false)
    private Feirante feirante;

    @Column(name = "data_pedido", nullable = false, insertable = false, updatable = false)
    private LocalDateTime dataPedido;

    @Column(name = "status_pedido", nullable = false, length = 30)
    private String status;

    @Column(name = "valor_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorTotal;

    @Column(name = "codigo_retirada", length = 6)
    private String codigoRetirada;
}
