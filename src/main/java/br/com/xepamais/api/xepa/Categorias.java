package br.com.xepamais.api.xepa;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Categorias {
    private Categorias() {}

    public static final List<String> DO_APP = List.of("Verduras e legumes", "Frutas", "Tempero", "Completa");

    private static final Map<String, String> APP_PARA_BANCO = Map.of(
            "verduras e legumes", "Verduras",
            "frutas", "Frutas",
            "tempero", "Temperos",
            "completa", "Outros");

    private static final Map<String, String> BANCO_PARA_APP = Map.of(
            "verduras", "Verduras e legumes",
            "legumes", "Verduras e legumes",
            "frutas", "Frutas",
            "temperos", "Tempero");

    public static String paraBanco(String categoriaDoApp) {
        return categoriaDoApp == null ? null : APP_PARA_BANCO.get(categoriaDoApp.trim().toLowerCase(Locale.ROOT));
    }

    public static String paraApp(String categoriaDoBanco) {
        if (categoriaDoBanco == null) return "Completa";
        return BANCO_PARA_APP.getOrDefault(categoriaDoBanco.trim().toLowerCase(Locale.ROOT), "Completa");
    }
}
