package br.com.xepamais.api.database.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

// Saque via Pix do saldo das vendas pelo Xepa+. Simulado: nenhum dinheiro é transferido.
@Entity
@Table(name = "tb_saque")
@Getter
@Setter
public class Saque {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_saque")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_feirante", nullable = false)
    private Feirante feirante;

    @Column(name = "valor_saque", nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Column(name = "tipo_chave_pix", nullable = false, length = 10)
    private String tipoChavePix;

    @Column(name = "chave_pix", nullable = false, length = 100)
    private String chavePix;

    @Column(name = "status_saque", nullable = false, length = 20)
    private String status;

    @Column(name = "id_transacao_gateway", length = 100)
    private String idTransacaoGateway;

    @Column(name = "data_solicitacao", nullable = false)
    private LocalDateTime dataSolicitacao;

    @Column(name = "data_conclusao")
    private LocalDateTime dataConclusao;
}
