package br.com.xepamais.api.database.model;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tb_pagamento")
@Getter
@Setter
public class Pagamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pagamento")
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "id_pedido", nullable = false)
    private Pedido pedido;

    @ManyToOne
    @JoinColumn(name = "id_tipo_pagamento", nullable = false)
    private TipoPagamento tipoPagamento;

    @Column(name = "valor_pagamento", nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Column(name = "status_pagamento", nullable = false, length = 20)
    private String status;

    @Column(name = "id_transacao_gateway", length = 100)
    private String idTransacaoGateway;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "data_criacao", nullable = false, insertable = false, updatable = false)
    private LocalDateTime dataCriacao;

    @Column(name = "data_pagamento")
    private LocalDateTime dataPagamento;
}
