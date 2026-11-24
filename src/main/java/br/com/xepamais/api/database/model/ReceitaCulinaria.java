package br.com.xepamais.api.database.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "tb_receita_culinaria")
@Getter
@Setter
public class ReceitaCulinaria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_receita")
    private Integer id;

    @Column(name = "titulo_receita", nullable = false, length = 120)
    private String titulo;

    @Column(name = "imagem_receita", length = 255)
    private String imagem;

    @Column(name = "modo_preparo", columnDefinition = "TEXT")
    private String modoPreparo;

    @Column(name = "tempo_preparo")
    private Integer tempoPreparo;
}
