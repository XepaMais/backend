package br.com.xepamais.api.database.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

// Receitas da Cozinha
@Entity
@Table(name = "tb_receita_culinaria")
@Getter
@Setter
public class ReceitaCulinaria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_receita")
    private Integer id;

    // Identificador fixo: o front usa para achar a foto que vem junto com o site
    @Column(name = "codigo_receita", length = 40, unique = true)
    private String codigo;

    @Column(name = "titulo_receita", nullable = false, length = 120)
    private String titulo;

    // Saladas, Sopas, Doces, Sucos, Aproveitamento integral ou Rápidas
    @Column(name = "categoria_receita", length = 40)
    private String categoria;

    // Fácil, Média ou Difícil
    @Column(name = "dificuldade_receita", length = 20)
    private String dificuldade;

    // Endereço de foto
    @Column(name = "imagem_receita", length = 255)
    private String imagem;

    @Column(name = "modo_preparo", columnDefinition = "TEXT")
    private String modoPreparo;

    // Em minutos
    @Column(name = "tempo_preparo")
    private Integer tempoPreparo;
}
