package br.com.xepamais.api.database.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "tb_consumidor")
@Getter
@Setter
public class Consumidor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_consumidor")
    private Integer id;

    @OneToOne
    @JoinColumn(name = "id_conta", nullable = false, unique = true)
    private Conta conta;
}
