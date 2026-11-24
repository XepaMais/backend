package br.com.xepamais.api.database.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "tb_categoria_gasto")
@Getter
@Setter
public class CategoriaGasto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_categoria_gasto")
    private Integer id;

    @Column(name = "nome_categoria_gasto", nullable = false, length = 80, unique = true)
    private String nome;

    @Column(name = "descricao_categoria_gasto", length = 150)
    private String descricao;
}
