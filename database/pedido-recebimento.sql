-- Área do consumidor: guarda no pedido como a xepa vai chegar (retirada na feira ou entrega em casa).
-- NÃO é obrigatório rodar: a API faz estas mesmas mudanças sozinha ao iniciar (config/AtualizacaoBanco.java).
-- Não rode em banco criado pelo script novo do time (xepaDB(1.0) (1).sql): ele já tem estas
-- colunas e o MySQL acusa "Duplicate column". Fica aqui como referência.
USE db_xepa;

ALTER TABLE tb_pedido
    ADD COLUMN tipo_recebimento VARCHAR(10)   NOT NULL DEFAULT 'RETIRADA' AFTER codigo_retirada,
    ADD COLUMN endereco_entrega VARCHAR(255)  NULL                         AFTER tipo_recebimento,
    ADD COLUMN taxa_entrega     DECIMAL(10,2) NOT NULL DEFAULT 0.00        AFTER endereco_entrega,
    ADD CONSTRAINT ck_pedido_recebimento CHECK (tipo_recebimento IN ('RETIRADA', 'ENTREGA')),
    ADD CONSTRAINT ck_pedido_taxa_entrega CHECK (taxa_entrega >= 0);
