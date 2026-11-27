package br.com.xepamais.api.database.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

// Planos do feirante: Semente, Broto e Colheita
@Entity
@Table(name = "tb_plano")
@Getter
@Setter
public class Plano {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_plano")
    private Integer id;

    // Mesmo id que o front usa: gratis, crescer ou pro
    @Column(name = "codigo_plano", length = 20, unique = true)
    private String codigo;

    // Colaborador que cadastrou o plano
    @ManyToOne
    @JoinColumn(name = "id_colaborador")
    private Colaborador colaborador;

    @Column(name = "nome_plano", nullable = false, length = 60, unique = true)
    private String nome;

    @Column(name = "descricao_plano", columnDefinition = "TEXT")
    private String descricao;

    @Column(name = "valor_plano", nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    // Taxa do Xepa+ sobre cada venda
    @Column(name = "percentual_comissao", nullable = false, precision = 5, scale = 2)
    private BigDecimal percentualComissao = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean ativo = true;
}
