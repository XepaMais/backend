package br.com.xepamais.api.database.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "tb_origem_renda")
@Getter
@Setter
public class OrigemRenda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_origem_renda")
    private Integer id;

    @Column(name = "nome_origem_renda", nullable = false, length = 60, unique = true)
    private String nome;
}
