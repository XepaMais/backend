package br.com.xepamais.api.database.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

// Mensagem da conversa com a Xepinha. papel: USUARIO ou IA
@Entity
@Table(name = "tb_mensagem_ia")
@Getter
@Setter
public class MensagemIa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_mensagem_ia")
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "id_conta", nullable = false)
    private Conta conta;

    // Coluna criada pela AtualizacaoBanco
    @ManyToOne
    @JoinColumn(name = "id_conversa_ia")
    private ConversaIa conversa;

    @Column(nullable = false, length = 10)
    private String papel;

    @Column(name = "texto_mensagem_ia", nullable = false, columnDefinition = "TEXT")
    private String texto;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "data_envio", nullable = false, updatable = false)
    private LocalDateTime dataEnvio;
}
