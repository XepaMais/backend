package br.com.xepamais.api.auth.dto;

// aceiteTermos: obrigatório (LGPD). aceiteMarketing: opcional, receber novidades por e-mail.
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
        String grupoComercio,
        Boolean aceiteTermos,
        Boolean aceiteMarketing
) {}
