package br.com.xepamais.api.auth.dto;

// token: mandar em "Authorization: Bearer <token>" nas rotas da conta (vale 30 dias).
// plano: código do plano do feirante (gratis/crescer/pro) ou null se ainda não escolheu (consumidor: null).
public record ContaResponse(Integer id, String nome, String email, String tipo, String token, String plano) {}
