package br.com.xepamais.api.auth.dto;

// aceiteTermos: obrigatório. aceiteMarketing: opcional, receber novidades por e-mail
public record CadastroConsumidorRequest(
        String nome,
        String cpf,
        String email,
        String senha,
        String telefone,
        Boolean aceiteTermos,
        Boolean aceiteMarketing
) {}
