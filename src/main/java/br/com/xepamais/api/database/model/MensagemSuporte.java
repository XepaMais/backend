package br.com.xepamais.api.database.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

// Mensagem enviada pela página "Suporte"
@Entity
@Table(name = "tb_mensagem_suporte")
@Getter
@Setter
public class MensagemSuporte {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_mensagem_suporte")
    private Integer id;

    // Conta de quem enviou, se estava logado
    @ManyToOne
    @JoinColumn(name = "id_conta")
    private Conta conta;

    @Column(name = "nome_remetente", nullable = false, length = 100)
    private String nome;

    @Column(name = "email_remetente", nullable = false, length = 100)
    private String email;

    // consumidor ou feirante
    @Column(name = "perfil_remetente", nullable = false, length = 12)
    private String perfil;

    @Column(name = "assunto", nullable = false, length = 150)
    private String assunto;

    @Column(name = "mensagem", nullable = false, columnDefinition = "TEXT")
    private String mensagem;

    // ABERTA ou RESPONDIDA
    @Column(name = "status_suporte", nullable = false, length = 12)
    private String status = "ABERTA";

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "data_envio", nullable = false, updatable = false)
    private LocalDateTime dataEnvio;
}
