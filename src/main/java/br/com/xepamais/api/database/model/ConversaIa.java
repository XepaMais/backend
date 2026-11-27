package br.com.xepamais.api.database.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

// Conversa do feirante com a Xepinha (as mensagens ficam em tb_mensagem_ia).
// Tabela criada pela AtualizacaoBanco.
@Entity
@Table(name = "tb_conversa_ia")
@Getter
@Setter
public class ConversaIa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_conversa_ia")
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "id_conta", nullable = false)
    private Conta conta;

    // Começo da primeira pergunta.
    @Column(name = "titulo_conversa", nullable = false, length = 60)
    private String titulo;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "data_criacao", nullable = false, updatable = false)
    private LocalDateTime dataCriacao;
}
