package br.com.xepamais.api.admin;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

// Lista as contas cadastradas com o tipo de cada uma
@Service
public class AdminUsuariosService {
    private final JdbcTemplate jdbc;

    public AdminUsuariosService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Usuario(int id, String nome, String email, String cpf, String telefone, boolean ativo,
                          String dataCadastro, String tipo, String nomeFantasia) {}

    public record Resumo(long total, long compartilhado, long feirante, long consumidor, long semTipo) {}

    public record Usuarios(Resumo resumo, List<Usuario> usuarios) {}

    public Usuarios listar() {
        List<Usuario> usuarios = jdbc.query("""
                SELECT c.id_conta, c.nome_conta, c.email_conta, c.cpf_conta, c.telefone_conta, c.ativo,
                       c.data_cadastro, f.id_feirante, f.nome_fantasia, co.id_consumidor
                FROM tb_conta c
                LEFT JOIN tb_feirante f ON f.id_conta = c.id_conta
                LEFT JOIN tb_consumidor co ON co.id_conta = c.id_conta
                ORDER BY c.id_conta DESC
                """, (rs, i) -> {
            boolean feirante = rs.getObject("id_feirante") != null;
            boolean consumidor = rs.getObject("id_consumidor") != null;
            String tipo = feirante && consumidor ? "COMPARTILHADO"
                    : feirante ? "FEIRANTE" : consumidor ? "CONSUMIDOR" : "SEM_TIPO";
            Object data = rs.getObject("data_cadastro");
            return new Usuario(rs.getInt("id_conta"), rs.getString("nome_conta"), rs.getString("email_conta"),
                    rs.getString("cpf_conta"), rs.getString("telefone_conta"), rs.getBoolean("ativo"),
                    data == null ? null : data.toString(), tipo, rs.getString("nome_fantasia"));
        });

        Resumo resumo = new Resumo(usuarios.size(),
                contar(usuarios, "COMPARTILHADO"), contar(usuarios, "FEIRANTE"),
                contar(usuarios, "CONSUMIDOR"), contar(usuarios, "SEM_TIPO"));
        return new Usuarios(resumo, usuarios);
    }

    private static long contar(List<Usuario> usuarios, String tipo) {
        long total = 0;
        for (Usuario u : usuarios) {
            if (u.tipo().equals(tipo)) {
                total++;
            }
        }
        return total;
    }
}
