package br.com.xepamais.api.database.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "tb_ingrediente_receita")
@Getter
@Setter
public class IngredienteReceita {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_ingrediente")
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "id_receita", nullable = false)
    private ReceitaCulinaria receita;

    @ManyToOne
    @JoinColumn(name = "id_categoria")
    private Categoria categoria;

    @Column(name = "nome_ingrediente", nullable = false, length = 100)
    private String nome;

    @Column(name = "quantidade_ingrediente", length = 50)
    private String quantidade;
}
