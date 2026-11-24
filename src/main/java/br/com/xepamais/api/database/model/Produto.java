package br.com.xepamais.api.database.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Table(name = "tb_produto")
@Getter
@Setter
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_produto")
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "id_feirante", nullable = false)
    private Feirante feirante;

    @ManyToOne
    @JoinColumn(name = "id_categoria", nullable = false)
    private Categoria categoria;

    @Column(name = "nome_produto", nullable = false, length = 100)
    private String nome;

    @Column(name = "descricao_produto", columnDefinition = "TEXT")
    private String descricao;

    @Column(name = "imagem_produto", length = 255)
    private String imagem;

    @Column(name = "unidade_medida", nullable = false, length = 10)
    private String unidadeMedida;

    @Column(name = "preco_referencia", precision = 10, scale = 2)
    private BigDecimal precoReferencia;

    @Column(nullable = false)
    private boolean ativo = true;
}
