package br.com.xepamais.api.admin;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Lê e altera qualquer tabela do banco para o painel admin.
// Nomes de tabela e coluna só entram no SQL depois de conferidos no information_schema.
@Service
public class AdminBancoService {
    static final String OCULTO = "••••••";
    private static final int TAMANHO_MAXIMO = 200;

    private final JdbcTemplate jdbc;

    public AdminBancoService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Coluna(String nome, String tipo, boolean chavePrimaria, boolean anulavel,
                         boolean autoIncremento, boolean oculta) {}

    public record Tabela(String nome, long linhas) {}

    public record Pagina(String tabela, List<Coluna> colunas, List<Map<String, Object>> linhas,
                         long total, int pagina, int tamanho) {}

    public List<Tabela> tabelas() {
        List<String> nomes = nomesTabelas();
        List<Tabela> resultado = new ArrayList<>();
        for (String nome : nomes) {
            Long total = jdbc.queryForObject("SELECT COUNT(*) FROM " + q(nome), Long.class);
            resultado.add(new Tabela(nome, total == null ? 0 : total));
        }
        return resultado;
    }

    public Pagina linhas(String tabela, int pagina, int tamanho, String busca) {
        String nome = conferirTabela(tabela);
        List<Coluna> colunas = colunas(nome);
        int tam = Math.max(1, Math.min(tamanho, TAMANHO_MAXIMO));
        int pag = Math.max(0, pagina);

        String filtro = "";
        List<Object> params = new ArrayList<>();
        if (busca != null && !busca.isBlank()) {
            StringBuilder concat = new StringBuilder("CONCAT_WS(' '");
            for (Coluna c : colunas) {
                if (!c.oculta()) concat.append(", ").append(q(c.nome()));
            }
            concat.append(")");
            filtro = " WHERE " + concat + " LIKE ?";
            params.add("%" + busca.trim() + "%");
        }

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM " + q(nome) + filtro, Long.class, params.toArray());

        String ordem = colunas.stream().filter(Coluna::chavePrimaria).findFirst()
                .map(c -> " ORDER BY " + q(c.nome()) + " DESC").orElse("");
        params.add(tam);
        params.add(pag * tam);
        List<Map<String, Object>> linhas = jdbc.query(
                "SELECT * FROM " + q(nome) + filtro + ordem + " LIMIT ? OFFSET ?",
                (rs, i) -> linha(rs, colunas), params.toArray());

        return new Pagina(nome, colunas, linhas, total == null ? 0 : total, pag, tam);
    }

    public void inserir(String tabela, Map<String, Object> valores) {
        String nome = conferirTabela(tabela);
        Map<String, Coluna> colunas = porNome(colunas(nome));
        List<String> nomes = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        valores.forEach((coluna, valor) -> {
            Coluna c = colunas.get(coluna);
            if (c == null || c.oculta()) throw invalido("Coluna inválida: " + coluna);
            if (c.autoIncremento() && vazio(valor)) return;
            nomes.add(q(c.nome()));
            params.add(normalizar(valor));
        });
        if (nomes.isEmpty()) throw invalido("Preencha ao menos um campo.");
        String marcadores = String.join(", ", nomes.stream().map(n -> "?").toList());
        executar("INSERT INTO " + q(nome) + " (" + String.join(", ", nomes) + ") VALUES (" + marcadores + ")", params);
    }

    public void atualizar(String tabela, String id, Map<String, Object> valores) {
        String nome = conferirTabela(tabela);
        List<Coluna> lista = colunas(nome);
        Coluna chave = chave(lista);
        Map<String, Coluna> colunas = porNome(lista);
        List<String> sets = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        valores.forEach((coluna, valor) -> {
            Coluna c = colunas.get(coluna);
            if (c == null || c.oculta()) throw invalido("Coluna inválida: " + coluna);
            if (c.chavePrimaria()) return;
            sets.add(q(c.nome()) + " = ?");
            params.add(normalizar(valor));
        });
        if (sets.isEmpty()) throw invalido("Nada para alterar.");
        params.add(id);
        executar("UPDATE " + q(nome) + " SET " + String.join(", ", sets) + " WHERE " + q(chave.nome()) + " = ?", params);
    }

    public void excluir(String tabela, String id) {
        String nome = conferirTabela(tabela);
        Coluna chave = chave(colunas(nome));
        executar("DELETE FROM " + q(nome) + " WHERE " + q(chave.nome()) + " = ?", List.of(id));
    }

    private void executar(String sql, List<Object> params) {
        try {
            int afetadas = jdbc.update(sql, params.toArray());
            if (afetadas == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro não encontrado.");
        } catch (DataAccessException ex) {
            Throwable causa = ex.getMostSpecificCause();
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "O banco recusou a operação: " + (causa != null ? causa.getMessage() : ex.getMessage()));
        }
    }

    private List<String> nomesTabelas() {
        return jdbc.queryForList(
                "SELECT TABLE_NAME FROM information_schema.TABLES "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE' ORDER BY TABLE_NAME",
                String.class);
    }

    private String conferirTabela(String tabela) {
        return nomesTabelas().stream().filter(t -> t.equalsIgnoreCase(tabela)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tabela não encontrada."));
    }

    private List<Coluna> colunas(String tabela) {
        return jdbc.query(
                "SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_KEY, IS_NULLABLE, EXTRA FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION",
                (rs, i) -> {
                    String nome = rs.getString("COLUMN_NAME");
                    return new Coluna(nome, rs.getString("COLUMN_TYPE"),
                            "PRI".equals(rs.getString("COLUMN_KEY")),
                            "YES".equals(rs.getString("IS_NULLABLE")),
                            rs.getString("EXTRA") != null && rs.getString("EXTRA").contains("auto_increment"),
                            sensivel(nome));
                }, tabela);
    }

    private static boolean sensivel(String coluna) {
        String c = coluna.toLowerCase(Locale.ROOT);
        return c.contains("senha") || c.contains("password") || c.contains("token") || c.contains("hash");
    }

    private static Coluna chave(List<Coluna> colunas) {
        List<Coluna> chaves = colunas.stream().filter(Coluna::chavePrimaria).toList();
        if (chaves.size() != 1) throw invalido("Esta tabela não tem uma chave primária simples; edite direto no banco.");
        return chaves.get(0);
    }

    private static Map<String, Coluna> porNome(List<Coluna> colunas) {
        Map<String, Coluna> mapa = new LinkedHashMap<>();
        colunas.forEach(c -> mapa.put(c.nome(), c));
        return mapa;
    }

    private static Map<String, Object> linha(ResultSet rs, List<Coluna> colunas) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        Map<String, Coluna> porNome = porNome(colunas);
        Map<String, Object> linha = new LinkedHashMap<>();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            String nome = meta.getColumnLabel(i);
            Object valor = rs.getObject(i);
            Coluna c = porNome.get(nome);
            if (c != null && c.oculta() && valor != null) {
                linha.put(nome, OCULTO);
            } else if (valor == null || valor instanceof Number || valor instanceof Boolean) {
                linha.put(nome, valor);
            } else if (valor instanceof byte[] bytes) {
                linha.put(nome, "[binário, " + bytes.length + " bytes]");
            } else {
                linha.put(nome, valor.toString());
            }
        }
        return linha;
    }

    private static Object normalizar(Object valor) {
        return valor instanceof String s && s.isEmpty() ? null : valor;
    }

    private static boolean vazio(Object valor) {
        return valor == null || (valor instanceof String s && s.isBlank());
    }

    private static String q(String identificador) {
        return "`" + identificador.replace("`", "``") + "`";
    }

    private static ResponseStatusException invalido(String mensagem) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
    }
}
