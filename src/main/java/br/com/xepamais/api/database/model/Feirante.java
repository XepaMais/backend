package br.com.xepamais.api.database.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.Set;

@Entity
@Table(name = "tb_feirante")
@Getter
@Setter
public class Feirante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_feirante")
    private Integer id;

    @OneToOne
    @JoinColumn(name = "id_conta", nullable = false, unique = true)
    private Conta conta;

    @ManyToOne
    @JoinColumn(name = "id_plano")
    private Plano plano;

    @Column(name = "nome_fantasia", nullable = false, length = 100)
    private String nomeFantasia;

    @Column(name = "cnpj_feirante", length = 14, unique = true)
    private String cnpj;

    @Column(name = "ccm_feirante", length = 20)
    private String ccm;

    @Column(name = "matricula_feirante", length = 20, unique = true)
    private String matricula;

    @Column(name = "grupo_comercio", length = 60)
    private String grupoComercio;

    @ManyToMany
    @JoinTable(name = "tb_feirante_feira",
            joinColumns = @JoinColumn(name = "id_feirante"),
            inverseJoinColumns = @JoinColumn(name = "id_feira"))
    private Set<Feira> feiras;
}
