package br.com.xepamais.api.database.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "tb_chat", uniqueConstraints = @UniqueConstraint(columnNames = {"id_consumidor", "id_feirante"}))
@Getter
@Setter
public class Chat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_chat")
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "id_consumidor", nullable = false)
    private Consumidor consumidor;

    @ManyToOne
    @JoinColumn(name = "id_feirante", nullable = false)
    private Feirante feirante;

    @Column(name = "data_inicio", nullable = false, insertable = false, updatable = false)
    private LocalDateTime dataInicio;
}
