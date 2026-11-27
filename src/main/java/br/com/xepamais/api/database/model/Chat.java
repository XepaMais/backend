package br.com.xepamais.api.database.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

// Conversa entre consumidor e feirante: uma por pedido, ou uma dúvida sobre uma xepa (sem pedido).
// Colunas novas criadas por database/nuvem-fotos-perfil-chat.sql.
@Entity
@Table(name = "tb_chat")
@Getter
@Setter
public class Chat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_chat")
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "id_consumidor", nullable = false)
    private Consumidor consumidor;

    @ManyToOne
    @JoinColumn(name = "id_feirante", nullable = false)
    private Feirante feirante;

    @OneToOne
    @JoinColumn(name = "id_pedido", unique = true)
    private Pedido pedido;

    @ManyToOne
    @JoinColumn(name = "id_xepa")
    private Xepa xepa;

    // RECENTE ou FINALIZADA
    @Column(name = "status_chat", nullable = false, length = 12)
    private String status = "RECENTE";

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "data_inicio", nullable = false, updatable = false)
    private LocalDateTime dataInicio;

    @Column(name = "data_finalizacao")
    private LocalDateTime dataFinalizacao;

    @Column(name = "leitura_consumidor")
    private LocalDateTime leituraConsumidor;

    @Column(name = "leitura_feirante")
    private LocalDateTime leituraFeirante;
}
