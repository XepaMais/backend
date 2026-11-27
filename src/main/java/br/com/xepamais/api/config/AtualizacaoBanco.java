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
// (as mesmas de database/pedido-recebimento.sql, nuvem-fotos-perfil-chat.sql e planos-endereco.sql).
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

        // Planos: código igual ao do front (gratis/crescer/pro); os planos padrão não têm colaborador.
        coluna("tb_plano", "codigo_plano", "VARCHAR(20) NULL AFTER id_plano");
        if (existeColuna("tb_plano", "codigo_plano") && !existeIndice("tb_plano", "uk_plano_codigo")) {
            executar("uk_plano_codigo", "ALTER TABLE tb_plano ADD CONSTRAINT uk_plano_codigo UNIQUE (codigo_plano)");
        }
        if (colunaObrigatoria("tb_plano", "id_colaborador")) {
            executar("tb_plano.id_colaborador opcional", "ALTER TABLE tb_plano MODIFY id_colaborador INT NULL");
        }

        // Dispositivos conectados: tipo do aparelho e navegador de cada login.
        coluna("tb_dispositivo_sessao", "tipo_aparelho", "VARCHAR(12) NULL AFTER nome_aparelho");
        coluna("tb_dispositivo_sessao", "navegador", "VARCHAR(60) NULL AFTER tipo_aparelho");

        // Xepinha: conversas com a IA (as mensagens já existiam em tb_mensagem_ia, sem conversa).
        executar("tabela tb_conversa_ia", """
                CREATE TABLE IF NOT EXISTS tb_conversa_ia (
                    id_conversa_ia  INT AUTO_INCREMENT PRIMARY KEY,
                    id_conta        INT         NOT NULL,
                    titulo_conversa VARCHAR(60) NOT NULL,
                    data_criacao    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_conversa_ia_conta (id_conta),
                    CONSTRAINT fk_conversa_ia_conta FOREIGN KEY (id_conta) REFERENCES tb_conta(id_conta) ON DELETE CASCADE
                )""");
        coluna("tb_mensagem_ia", "id_conversa_ia", "INT NULL AFTER id_conta");
        if (existeColuna("tb_mensagem_ia", "id_conversa_ia") && !existeRestricao("tb_mensagem_ia", "fk_mensagem_ia_conversa")) {
            executar("fk_mensagem_ia_conversa", "ALTER TABLE tb_mensagem_ia ADD CONSTRAINT fk_mensagem_ia_conversa "
                    + "FOREIGN KEY (id_conversa_ia) REFERENCES tb_conversa_ia(id_conversa_ia) ON DELETE CASCADE");
        }

        // Suporte: mensagens do formulário (a equipe vê no painel admin).
        executar("tabela tb_mensagem_suporte", """
                CREATE TABLE IF NOT EXISTS tb_mensagem_suporte (
                    id_mensagem_suporte INT AUTO_INCREMENT PRIMARY KEY,
                    id_conta            INT          NULL,
                    nome_remetente      VARCHAR(100) NOT NULL,
                    email_remetente     VARCHAR(100) NOT NULL,
                    perfil_remetente    VARCHAR(12)  NOT NULL,
                    assunto             VARCHAR(150) NOT NULL,
                    mensagem            TEXT         NOT NULL,
                    status_suporte      VARCHAR(12)  NOT NULL DEFAULT 'ABERTA',
                    data_envio          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT fk_suporte_conta FOREIGN KEY (id_conta) REFERENCES tb_conta(id_conta) ON DELETE SET NULL
                )""");

        // Funcionários de cada barraca em cada feira (igual ao esquema do time: o funcionário
        // pertence ao vínculo feirante–feira de tb_feirante_feira).
        executar("tabela tb_funcionario_feira", """
                CREATE TABLE IF NOT EXISTS tb_funcionario_feira (
                    id_funcionario   INT AUTO_INCREMENT PRIMARY KEY,
                    id_feirante      INT          NOT NULL,
                    id_feira         INT          NOT NULL,
                    nome_funcionario VARCHAR(100) NOT NULL,
                    CONSTRAINT fk_funcionario_feirante_feira FOREIGN KEY (id_feirante, id_feira)
                        REFERENCES tb_feirante_feira(id_feirante, id_feira) ON DELETE CASCADE
                )""");

        // Receitas da Cozinha: código (liga à foto do front), categoria e dificuldade.
        coluna("tb_receita_culinaria", "codigo_receita", "VARCHAR(40) NULL AFTER id_receita");
        coluna("tb_receita_culinaria", "categoria_receita", "VARCHAR(40) NULL AFTER titulo_receita");
        coluna("tb_receita_culinaria", "dificuldade_receita", "VARCHAR(20) NULL AFTER categoria_receita");
        if (existeColuna("tb_receita_culinaria", "codigo_receita") && !existeIndice("tb_receita_culinaria", "uk_receita_codigo")) {
            executar("uk_receita_codigo", "ALTER TABLE tb_receita_culinaria ADD CONSTRAINT uk_receita_codigo UNIQUE (codigo_receita)");
        }

        // Endereço de entrega do consumidor (o último usado) e a forma de receber preferida.
        coluna("tb_consumidor", "recebimento_preferido", "VARCHAR(10) NOT NULL DEFAULT 'RETIRADA'");
        coluna("tb_consumidor", "cep_entrega", "CHAR(8) NULL");
        coluna("tb_consumidor", "logradouro_entrega", "VARCHAR(150) NULL");
        coluna("tb_consumidor", "numero_entrega", "VARCHAR(10) NULL");
        coluna("tb_consumidor", "complemento_entrega", "VARCHAR(60) NULL");
        coluna("tb_consumidor", "bairro_entrega", "VARCHAR(80) NULL");
        coluna("tb_consumidor", "cidade_entrega", "VARCHAR(80) NULL");
        coluna("tb_consumidor", "uf_entrega", "CHAR(2) NULL");

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
        // LGPD: registro do consentimento e da exclusão da conta.
        coluna("tb_conta", "aceite_termos_em", "DATETIME NULL");
        coluna("tb_conta", "versao_termos", "VARCHAR(10) NULL");
        coluna("tb_conta", "aceite_marketing", "BOOLEAN NOT NULL DEFAULT FALSE");
        coluna("tb_conta", "data_exclusao", "DATETIME NULL");
        coluna("tb_feirante", "validade_matricula", "VARCHAR(20) NULL");
        // O script base do time cria a validade como DATE, mas o front envia texto livre
        // (ex.: "31/03/2027"): com DATE, salvar o perfil do feirante dá "Incorrect date value".
        if ("date".equals(tipoColuna("tb_feirante", "validade_matricula"))) {
            executar("tb_feirante.validade_matricula como texto",
                    "ALTER TABLE tb_feirante MODIFY validade_matricula VARCHAR(20) NULL");
        }
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

    private String tipoColuna(String tabela, String coluna) {
        return jdbc.query("SELECT DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                        + "AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                rs -> rs.next() ? rs.getString(1).toLowerCase() : null, tabela, coluna);
    }

    private boolean colunaObrigatoria(String tabela, String coluna) {
        return contar("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                + "AND TABLE_NAME = ? AND COLUMN_NAME = ? AND IS_NULLABLE = 'NO'", tabela, coluna) > 0;
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
