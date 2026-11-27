package br.com.xepamais.api.auth;

import java.util.Locale;
import java.util.Map;

// Contas fictícias da demonstração
public final class ContaDemo {
    public static final String DOMINIO = "@demo.xepamais.com.br";

    // Perfil escolhido na tela /demo -> conta usada
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
