package br.com.xepamais.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

// Ao iniciar, a API cria no MySQL as tabelas e colunas novas que ainda faltam
// (as mesmas de database/pedido-recebimento.sql e database/nuvem-fotos-perfil-chat.sql).
// Assim nenhum banco (local ou da EC2) fica sem elas e quebra com "Unknown column".
// Cada passo confere antes se já existe: rodar de novo não muda nada.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AtualizacaoBanco implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AtualizacaoBanco.class);

    private final JdbcTemplate jdbc;
    private final DataSource dataSource;

    public AtualizacaoBanco(JdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!ehMySql()) return; // Nos testes (H2) o Hibernate já cria tudo.

        // Pedido: como a xepa chega (retirada ou entrega) e comissão.
        coluna("tb_pedido", "percentual_comissao", "DECIMAL(5,2) NOT NULL DEFAULT 0.00");
        coluna("tb_pedido", "valor_comissao", "DECIMAL(10,2) NOT NULL DEFAULT 0.00");
        coluna("tb_pedido", "tipo_recebimento", "VARCHAR(10) NOT NULL DEFAULT 'RETIRADA'");
        coluna("tb_pedido", "endereco_entrega", "VARCHAR(255) NULL");
        coluna("tb_pedido", "taxa_entrega", "DECIMAL(10,2) NOT NULL DEFAULT 0.00");
        coluna("tb_plano", "percentual_comissao", "DECIMAL(5,2) NOT NULL DEFAULT 0.00");

        // Fotos (xepas e perfil).
        executar("tabela tb_imagem", """
                CREATE TABLE IF NOT EXISTS tb_imagem (
                    id_imagem     INT AUTO_INCREMENT PRIMARY KEY,
                    id_conta      INT         NOT NULL,
                    tipo_conteudo VARCHAR(40) NOT NULL,
                    dados_imagem  MEDIUMBLOB  NOT NULL,
                    data_cadastro DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT fk_imagem_conta FOREIGN KEY (id_conta) REFERENCES tb_conta(id_conta) ON DELETE CASCADE
                )""");

        // Perfil.
        coluna("tb_conta", "foto_conta", "VARCHAR(255) NULL");
        coluna("tb_conta", "data_nascimento", "DATE NULL");
        coluna("tb_feirante", "validade_matricula", "VARCHAR(20) NULL");
        coluna("tb_feirante", "titular_feirante", "VARCHAR(100) NULL");

        // Chat: uma conversa por pedido (ou dúvida sobre uma xepa).
        if (existeIndice("tb_chat", "uk_chat_par")) {
            if (!existeIndice("tb_chat", "idx_chat_consumidor")) {
                executar("índice idx_chat_consumidor", "ALTER TABLE tb_chat ADD INDEX idx_chat_consumidor (id_consumidor)");
            }
            executar("remover uk_chat_par", "ALTER TABLE tb_chat DROP INDEX uk_chat_par");
        }
        coluna("tb_chat", "id_pedido", "INT NULL");
        coluna("tb_chat", "id_xepa", "INT NULL");
        coluna("tb_chat", "status_chat", "VARCHAR(12) NOT NULL DEFAULT 'RECENTE'");
        coluna("tb_chat", "data_finalizacao", "DATETIME NULL");
        coluna("tb_chat", "leitura_consumidor", "DATETIME NULL");
        coluna("tb_chat", "leitura_feirante", "DATETIME NULL");
        if (!existeIndice("tb_chat", "uk_chat_pedido")) {
            executar("uk_chat_pedido", "ALTER TABLE tb_chat ADD CONSTRAINT uk_chat_pedido UNIQUE (id_pedido)");
        }
        if (!existeRestricao("tb_chat", "fk_chat_pedido")) {
            executar("fk_chat_pedido", "ALTER TABLE tb_chat ADD CONSTRAINT fk_chat_pedido "
                    + "FOREIGN KEY (id_pedido) REFERENCES tb_pedido(id_pedido)");
        }
        if (!existeRestricao("tb_chat", "fk_chat_xepa")) {
            executar("fk_chat_xepa", "ALTER TABLE tb_chat ADD CONSTRAINT fk_chat_xepa "
                    + "FOREIGN KEY (id_xepa) REFERENCES tb_xepa(id_xepa)");
        }
    }

    private boolean ehMySql() {
        try (Connection conexao = dataSource.getConnection()) {
            return conexao.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql");
        } catch (Exception ex) {
            log.warn("Não foi possível identificar o banco: {}", ex.getMessage());
            return false;
        }
    }

    private void coluna(String tabela, String coluna, String definicao) {
        if (!existeTabela(tabela) || existeColuna(tabela, coluna)) return;
        executar(tabela + "." + coluna, "ALTER TABLE " + tabela + " ADD COLUMN " + coluna + " " + definicao);
    }

    private boolean existeTabela(String tabela) {
        return contar("SELECT COUNT(*) FROM information_schema.TABLES "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?", tabela) > 0;
    }

    private boolean existeColuna(String tabela, String coluna) {
        return contar("SELECT COUNT(*) FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?", tabela, coluna) > 0;
    }

    private boolean existeIndice(String tabela, String indice) {
        return contar("SELECT COUNT(*) FROM information_schema.STATISTICS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?", tabela, indice) > 0;
    }

    private boolean existeRestricao(String tabela, String restricao) {
        return contar("SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND CONSTRAINT_NAME = ?", tabela, restricao) > 0;
    }

    private int contar(String sql, Object... parametros) {
        Integer total = jdbc.queryForObject(sql, Integer.class, parametros);
        return total == null ? 0 : total;
    }

    // Um passo que falha não derruba a API: fica registrado no log para corrigir à mão.
    private void executar(String descricao, String sql) {
        try {
            jdbc.execute(sql);
            log.info("Banco atualizado: {}", descricao);
        } catch (Exception ex) {
            log.warn("Não foi possível atualizar o banco ({}): {}", descricao, ex.getMessage());
        }
    }
}
