package br.com.xepamais.api.database.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "tb_carrinho")
@Getter
@Setter
public class Carrinho {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_carrinho")
    private Integer id;

    @OneToOne
    @JoinColumn(name = "id_consumidor", nullable = false, unique = true)
    private Consumidor consumidor;

    @Column(name = "data_criacao", nullable = false, insertable = false, updatable = false)
    private LocalDateTime dataCriacao;
}
