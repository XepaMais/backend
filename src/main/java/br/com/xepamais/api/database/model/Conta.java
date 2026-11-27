package br.com.xepamais.api.database.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
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

    @Column(name = "nome_conta", length = 100)
    private String nome;

    @Column(name = "cpf_conta", length = 11)
    @JdbcTypeCode(SqlTypes.CHAR) // CHAR(11) no MySQL
    private String cpf;

    @Column(name = "email_conta", length = 100)
    private String email;

    @Column(name= "senha_conta")
    private String senha;

    @Column(name = "telefone_conta", length = 20)
    private String telefone;

    // Endereço da foto (ex.: /api/imagens/12). Coluna criada por database/nuvem-fotos-perfil-chat.sql.
    @Column(name = "foto_conta", length = 255)
    private String foto;

    @Column(name = "data_nascimento")
    private java.time.LocalDate dataNascimento;

    private boolean ativo = true;

    @Column(name = "data_cadastro", insertable = false, updatable = false)
    private LocalDateTime dataCadastro;

    // LGPD: quando e em qual versão a pessoa aceitou os Termos de Uso e a Política de Privacidade.
    @Column(name = "aceite_termos_em")
    private LocalDateTime aceiteTermosEm;

    @Column(name = "versao_termos", length = 10)
    private String versaoTermos;

    // Consentimento opcional para receber novidades por e-mail (pode ser revogado no perfil).
    @Column(name = "aceite_marketing", nullable = false)
    private boolean aceiteMarketing = false;

    // Data em que a conta foi excluída a pedido do titular (dados pessoais anonimizados).
    @Column(name = "data_exclusao")
    private LocalDateTime dataExclusao;







}
