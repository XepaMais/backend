package br.com.xepamais.api.database.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "tb_feira")
@Getter
@Setter
public class Feira {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_feira")
    private Integer id;

    @Column(name = "nome_feira", nullable = false, length = 100)
    private String nome;

    @Column(name = "endereco_feira", nullable = false, length = 150)
    private String endereco;

    @Column(name = "numero_feira", length = 10)
    private String numero;

    @Column(name = "cep_feira", length = 8)
    private String cep;

    @Column(name = "cidade_feira", nullable = false, length = 80)
    private String cidade;

    @Column(name = "uf_feira", nullable = false, length = 2)
    private String uf;

    // Dias da feira ficam em tb_feira_dia: 1=segunda ... 7=domingo (igual ao DayOfWeek do Java).
    @ElementCollection
    @CollectionTable(name = "tb_feira_dia", joinColumns = @JoinColumn(name = "id_feira"))
    @Column(name = "dia_semana")
    @JdbcTypeCode(SqlTypes.TINYINT)
    private Set<Integer> dias = new HashSet<>();

    @Column(name = "horario_inicio")
    private LocalTime horarioInicio;

    @Column(name = "horario_fim")
    private LocalTime horarioFim;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;
}
