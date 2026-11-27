package br.com.xepamais.api.database.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hibernate.annotations.ColumnDefault;
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

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "data_pedido", nullable = false, updatable = false)
    private LocalDateTime dataPedido;

    @Column(name = "status_pedido", nullable = false, length = 30)
    private String status;

    @Column(name = "valor_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorTotal;

    // "Foto" da comissão do plano do feirante no momento da compra.
    @Column(name = "percentual_comissao", nullable = false, precision = 5, scale = 2)
    private BigDecimal percentualComissao = BigDecimal.ZERO;

    @Column(name = "valor_comissao", nullable = false, precision = 10, scale = 2)
    private BigDecimal valorComissao = BigDecimal.ZERO;

    @Column(name = "codigo_retirada", length = 4)
    @JdbcTypeCode(SqlTypes.CHAR) // CHAR(4) no MySQL: código de 4 dígitos
    private String codigoRetirada;

    // RETIRADA (na feira) ou ENTREGA (em casa). Colunas criadas por database/pedido-recebimento.sql.
    @Column(name = "tipo_recebimento", nullable = false, length = 10)
    private String tipoRecebimento = "RETIRADA";

    @Column(name = "endereco_entrega", length = 255)
    private String enderecoEntrega;

    @Column(name = "taxa_entrega", nullable = false, precision = 10, scale = 2)
    private BigDecimal taxaEntrega = BigDecimal.ZERO;
}
