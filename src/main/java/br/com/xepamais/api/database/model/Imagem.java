package br.com.xepamais.api.database.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

// Foto enviada por uma conta
@Entity
@Table(name = "tb_imagem")
@Getter
@Setter
public class Imagem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_imagem")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_conta", nullable = false)
    private Conta conta;

    @Column(name = "tipo_conteudo", nullable = false, length = 40)
    private String tipoConteudo;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "dados_imagem", nullable = false, length = 16777215) // MEDIUMBLOB no MySQL (até 16 MB)
    private byte[] dados;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "data_cadastro", insertable = false, updatable = false)
    private LocalDateTime dataCadastro;
}
