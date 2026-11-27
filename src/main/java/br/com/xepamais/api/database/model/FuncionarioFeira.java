package br.com.xepamais.api.database.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

// Quem atende na barraca do feirante em uma feira (aparece no perfil do feirante para o consumidor).
// Tabela criada pela AtualizacaoBanco.
@Entity
@Table(name = "tb_funcionario_feira")
@Getter
@Setter
public class FuncionarioFeira {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_funcionario")
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "id_feirante", nullable = false)
    private Feirante feirante;

    @ManyToOne
    @JoinColumn(name = "id_feira", nullable = false)
    private Feira feira;

    @Column(name = "nome_funcionario", nullable = false, length = 80)
    private String nome;
}
