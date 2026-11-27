-- Planos do feirante e endereço de entrega do consumidor.

USE db_xepa;

-- Planos: código igual ao do front e planos padrão sem colaborador.
ALTER TABLE tb_plano
    ADD COLUMN codigo_plano VARCHAR(20) NULL AFTER id_plano,
    ADD CONSTRAINT uk_plano_codigo UNIQUE (codigo_plano),
    MODIFY id_colaborador INT NULL;

INSERT INTO tb_plano (codigo_plano, nome_plano, descricao_plano, valor_plano, percentual_comissao, ativo) VALUES
    ('gratis',  'Semente',  'Ideal para quem está começando agora e quer dar os primeiros passos sem nenhum investimento inicial.', 0.00, 15.00, TRUE),
    ('crescer', 'Broto',    'Perfeito para negócios em expansão que precisam de mais flexibilidade e recursos avançados.',          29.90, 8.00, TRUE),
    ('pro',     'Colheita', 'A solução completa para feirante que busca alta performance, controle total e máxima produtividade.', 59.90, 5.00, TRUE);

-- Endereço de entrega (o último usado) e forma de receber preferida do consumidor.
ALTER TABLE tb_consumidor
    ADD COLUMN recebimento_preferido VARCHAR(10)  NOT NULL DEFAULT 'RETIRADA',
    ADD COLUMN cep_entrega           CHAR(8)      NULL,
    ADD COLUMN logradouro_entrega    VARCHAR(150) NULL,
    ADD COLUMN numero_entrega        VARCHAR(10)  NULL,
    ADD COLUMN complemento_entrega   VARCHAR(60)  NULL,
    ADD COLUMN bairro_entrega        VARCHAR(80)  NULL,
    ADD COLUMN cidade_entrega        VARCHAR(80)  NULL,
    ADD COLUMN uf_entrega            CHAR(2)      NULL,
    ADD CONSTRAINT ck_consumidor_recebimento CHECK (recebimento_preferido IN ('RETIRADA', 'ENTREGA'));
