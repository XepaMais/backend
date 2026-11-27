-- Tudo na nuvem: fotos, dados do perfil e chat ficam no banco (antes ficavam no navegador).
-- NÃO é obrigatório rodar: a API faz estas mesmas mudanças sozinha ao iniciar (config/AtualizacaoBanco.java).
-- Não rode em banco criado pelo script novo do time (xepaDB(1.0) (1).sql): ele já tem estas
-- colunas e o MySQL acusa "Duplicate column". Fica aqui como referência.
USE db_xepa;

-- Fotos (xepas e perfil). Guardadas no banco e servidas por GET /api/imagens/{id}.
CREATE TABLE tb_imagem (
    id_imagem     INT AUTO_INCREMENT PRIMARY KEY,
    id_conta      INT          NOT NULL,             -- quem enviou
    tipo_conteudo VARCHAR(40)  NOT NULL,             -- image/jpeg, image/png, image/webp
    dados_imagem  MEDIUMBLOB   NOT NULL,             -- até 16 MB (a API aceita até 2 MB)
    data_cadastro DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_imagem_tipo CHECK (tipo_conteudo IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT fk_imagem_conta FOREIGN KEY (id_conta) REFERENCES tb_conta(id_conta) ON DELETE CASCADE
);

-- Perfil: foto e data de nascimento da conta.
ALTER TABLE tb_conta
    ADD COLUMN foto_conta      VARCHAR(255) NULL AFTER telefone_conta,
    ADD COLUMN data_nascimento DATE         NULL AFTER foto_conta;

-- Perfil do feirante: validade da matrícula e titular.
ALTER TABLE tb_feirante
    ADD COLUMN validade_matricula VARCHAR(20)  NULL AFTER matricula_feirante,
    ADD COLUMN titular_feirante   VARCHAR(100) NULL AFTER validade_matricula;

-- Chat: uma conversa por pedido (ou por dúvida sobre uma xepa), com status e leitura.
-- O índice novo substitui o UNIQUE (id_consumidor, id_feirante), que a chave estrangeira usava.
ALTER TABLE tb_chat ADD INDEX idx_chat_consumidor (id_consumidor);
ALTER TABLE tb_chat DROP INDEX uk_chat_par;
ALTER TABLE tb_chat
    ADD COLUMN id_pedido          INT         NULL AFTER id_feirante,
    ADD COLUMN id_xepa            INT         NULL AFTER id_pedido,
    ADD COLUMN status_chat        VARCHAR(12) NOT NULL DEFAULT 'RECENTE' AFTER id_xepa,
    ADD COLUMN data_finalizacao   DATETIME    NULL AFTER data_inicio,
    ADD COLUMN leitura_consumidor DATETIME    NULL AFTER data_finalizacao,
    ADD COLUMN leitura_feirante   DATETIME    NULL AFTER leitura_consumidor,
    ADD CONSTRAINT uk_chat_pedido UNIQUE (id_pedido),
    ADD CONSTRAINT ck_chat_status CHECK (status_chat IN ('RECENTE', 'FINALIZADA')),
    ADD CONSTRAINT fk_chat_pedido FOREIGN KEY (id_pedido) REFERENCES tb_pedido(id_pedido),
    ADD CONSTRAINT fk_chat_xepa   FOREIGN KEY (id_xepa)   REFERENCES tb_xepa(id_xepa);
