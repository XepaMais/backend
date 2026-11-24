package br.com.xepamais.api.auth.dto;

public record CadastroConsumidorRequest(
        String nome,
        String cpf,
        String email,
        String senha,
        String telefone
) {}
