package br.com.xepamais.api.auth.dto;

// token
public record ContaResponse(Integer id, String nome, String email, String tipo, String token, String plano) {}
