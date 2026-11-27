package br.com.xepamais.api.database.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

// Um login = uma sessão (um aparelho). O token do login leva o id da sessão:
// sessão revogada ou vencida = token recusado (o aparelho é desconectado).
@Entity
@Table(name = "tb_dispositivo_sessao")
@Getter
@Setter
public class DispositivoSessao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_dispositivo")
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "id_conta", nullable = false)
    private Conta conta;

    // Sistema do aparelho (ex.: Windows, Android, iPhone).
    @Column(name = "nome_aparelho", length = 100)
    private String nomeAparelho;

    // computador, celular ou tablet. Colunas novas criadas pela AtualizacaoBanco.
    @Column(name = "tipo_aparelho", length = 12)
    private String tipoAparelho;

    @Column(name = "navegador", length = 60)
    private String navegador;

    // Identificador aleatório da sessão (SHA-256 em hexadecimal). O token não é guardado.
    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "data_criacao", nullable = false, updatable = false)
    private LocalDateTime dataCriacao;

    @Column(name = "expira_em", nullable = false)
    private LocalDateTime expiraEm;

    @Column(name = "ultimo_acesso")
    private LocalDateTime ultimoAcesso;

    @Column(nullable = false)
    private boolean revogado;
}
