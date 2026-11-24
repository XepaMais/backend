package br.com.xepamais.api.database.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Table(name = "tb_plano")
@Getter
@Setter
public class Plano {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_plano")
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "id_colaborador", nullable = false)
    private Colaborador colaborador;

    @Column(name = "nome_plano", nullable = false, length = 60, unique = true)
    private String nome;

    @Column(name = "descricao_plano", columnDefinition = "TEXT")
    private String descricao;

    @Column(name = "valor_plano", nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Column(nullable = false)
    private boolean ativo = true;
}
