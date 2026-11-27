package br.com.xepamais.api.database.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "tb_conta")
@Getter
@Setter

public class Conta{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_conta")
    private Integer id;

    @Column(name = "nome_conta")
    private String nome;

    @Column(name = "cpf_conta")
    private String cpf;

    @Column(name = "email_conta")
    private String email;

    @Column(name= "senha_conta")
    private String senha;

    @Column(name = "telefone_conta")
    private String telefone;

    // Endereço da foto (ex.: /api/imagens/12). Coluna criada por database/nuvem-fotos-perfil-chat.sql.
    @Column(name = "foto_conta", length = 255)
    private String foto;

    @Column(name = "data_nascimento")
    private java.time.LocalDate dataNascimento;

    private boolean ativo = true;

    @Column(name = "data_cadastro", insertable = false, updatable = false)
    private LocalDateTime dataCadastro;








}
