package br.com.xepamais.api.database.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "tb_mensagem")
@Getter
@Setter
public class Mensagem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_mensagem")
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "id_chat", nullable = false)
    private Chat chat;

    @ManyToOne
    @JoinColumn(name = "id_conta", nullable = false)
    private Conta conta;

    @Column(name = "texto_mensagem", nullable = false, columnDefinition = "TEXT")
    private String texto;

    @Column(name = "data_envio", nullable = false, insertable = false, updatable = false)
    private LocalDateTime dataEnvio;

    @Column(nullable = false)
    private boolean lida;
}
