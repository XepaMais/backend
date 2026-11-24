package br.com.xepamais.api.database.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "tb_categoria_despesa")
@Getter
@Setter
public class CategoriaDespesa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_categoria_despesa")
    private Integer id;

    @Column(name = "nome_categoria_despesa", nullable = false, length = 80, unique = true)
    private String nome;

    @Column(name = "descricao_categoria_despesa", length = 150)
    private String descricao;
}
