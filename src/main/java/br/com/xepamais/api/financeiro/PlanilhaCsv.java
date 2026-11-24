package br.com.xepamais.api.financeiro;

import br.com.xepamais.api.financeiro.dto.FinanceiroDtos.HistoricoResponse;
import br.com.xepamais.api.financeiro.dto.FinanceiroDtos.RegistroHistorico;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

final class PlanilhaCsv {
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private PlanilhaCsv() {}

    static String gerar(String nomeFeirante, HistoricoResponse historico) {
        StringBuilder csv = new StringBuilder().append((char) 0xFEFF);
        linha(csv, "Histórico financeiro - Xepa+");
        linha(csv, "Feirante", nomeFeirante);
        linha(csv);
        linha(csv, "Data", "Descrição", "Categoria", "Tipo", "Feira", "Valor (R$)");
        for (RegistroHistorico registro : historico.registros()) {
            linha(csv, DATA.format(registro.data()), registro.descricao(), registro.categoria(),
                    registro.tipo().equals("lucro") ? "Lucro" : "Despesa",
                    registro.feira(), numero(registro.valor()));
        }
        linha(csv);
        linha(csv, "Total de lucros", "", "", "", "", numero(historico.lucros()));
        linha(csv, "Total de despesas", "", "", "", "", numero(historico.gastos()));
        linha(csv, "Saldo do período", "", "", "", "", numero(historico.saldo()));
        return csv.toString();
    }

    private static void linha(StringBuilder csv, String... campos) {
        for (int i = 0; i < campos.length; i++) {
            if (i > 0) csv.append(';');
            csv.append(campo(campos[i]));
        }
        csv.append("\r\n");
    }

    private static String campo(String valor) {
        String texto = valor == null ? "" : valor;
        // Evita que o Excel execute textos digitados pelo usuário como fórmula (=, +, -, @).
        if (!texto.isEmpty() && "=+-@\t\r".indexOf(texto.charAt(0)) >= 0 && !ehNumero(texto)) {
            texto = "'" + texto;
        }
        if (texto.contains(";") || texto.contains("\"") || texto.contains("\n") || texto.contains("\r")) {
            texto = "\"" + texto.replace("\"", "\"\"") + "\"";
        }
        return texto;
    }

    private static boolean ehNumero(String texto) {
        return texto.matches("-?\\d+(,\\d+)?");
    }

    private static String numero(BigDecimal valor) {
        return valor.toPlainString().replace('.', ',');
    }
}
