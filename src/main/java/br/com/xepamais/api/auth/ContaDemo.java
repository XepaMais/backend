package br.com.xepamais.api.auth;

import java.util.Locale;
import java.util.Map;

// Contas fictícias da demonstração (criadas pelo 04-seed-demo.sql do repositório database).
// O botão "Visite nosso Demo" entra nelas sem senha (POST /api/auth/demo), e os dados ficam na API:
// quem abre a demonstração em qualquer aparelho vê as mesmas feiras, xepas, pedidos e financeiro.
public final class ContaDemo {
    public static final String DOMINIO = "@demo.xepamais.com.br";

    // Perfil escolhido na tela /demo -> conta usada. Os feirantes estão nos planos Semente, Broto e Colheita.
    public static final Map<String, String> CONTAS = Map.of(
            "consumidor", "henrique.mazza" + DOMINIO,
            "gratis", "sebastiao.ferreira" + DOMINIO,
            "crescer", "rosangela.matos" + DOMINIO,
            "pro", "antonio.nakamura" + DOMINIO);

    private ContaDemo() {}

    public static boolean ehDemo(String email) {
        return email != null && email.trim().toLowerCase(Locale.ROOT).endsWith(DOMINIO);
    }
}
