package br.com.xepamais.api.auth.dto;

public record CadastroFeiranteRequest(
        String nome,
        String cpf,
        String email,
        String senha,
        String telefone,
        String nomeFantasia,
        String cnpj,
        String ccm,
        String matricula,
        String grupoComercio
) {}
