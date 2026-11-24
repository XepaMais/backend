package br.com.xepamais.api.database.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

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

    @Column(nullable = false, length = 10)
    private String papel;

    @Column(name = "texto_mensagem_ia", nullable = false, columnDefinition = "TEXT")
    private String texto;

    @Column(name = "data_envio", nullable = false, insertable = false, updatable = false)
    private LocalDateTime dataEnvio;
}
