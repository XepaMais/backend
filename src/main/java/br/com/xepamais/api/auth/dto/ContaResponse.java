package br.com.xepamais.api.auth.dto;

// token: mandar em "Authorization: Bearer <token>" nas rotas da conta (vale 30 dias).
public record ContaResponse(Integer id, String nome, String email, String tipo, String token) {}
