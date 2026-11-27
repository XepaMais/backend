-- Área do consumidor: guarda no pedido como a xepa vai chegar (retirada na feira ou entrega em casa).

USE db_xepa;

ALTER TABLE tb_pedido
    ADD COLUMN tipo_recebimento VARCHAR(10)   NOT NULL DEFAULT 'RETIRADA' AFTER codigo_retirada,
    ADD COLUMN endereco_entrega VARCHAR(255)  NULL                         AFTER tipo_recebimento,
    ADD COLUMN taxa_entrega     DECIMAL(10,2) NOT NULL DEFAULT 0.00        AFTER endereco_entrega,
    ADD CONSTRAINT ck_pedido_recebimento CHECK (tipo_recebimento IN ('RETIRADA', 'ENTREGA')),
    ADD CONSTRAINT ck_pedido_taxa_entrega CHECK (taxa_entrega >= 0);
