-- Feiras de exemplo para o mapa, só adiciona linhas e não altera nenhuma tabela
USE db_xepa;

INSERT INTO tb_feira (nome_feira, endereco_feira, numero_feira, cep_feira, cidade_feira, uf_feira,
                      horario_inicio, horario_fim, latitude, longitude) VALUES
    ('Feira Livre da Lapa',  'Rua Guaicurus',          's/n', '05033000', 'São Paulo', 'SP', '07:00', '13:00', -23.522800, -46.701200),
    ('Feira de Pinheiros',   'Rua Cardeal Arcoverde',  's/n', '05407001', 'São Paulo', 'SP', '06:00', '14:00', -23.559400, -46.685600),
    ('Feira Xepa Central',   'Rua Clélia',             's/n', '05042000', 'São Paulo', 'SP', '08:00', '17:00', -23.528086, -46.691760);

-- Dias: 1=segunda ... 7=domingo
INSERT INTO tb_feira_dia (id_feira, dia_semana)
SELECT id_feira, d.dia FROM tb_feira
JOIN (SELECT 2 dia UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7) d
WHERE nome_feira = 'Feira Livre da Lapa';

INSERT INTO tb_feira_dia (id_feira, dia_semana)
SELECT id_feira, d.dia FROM tb_feira
JOIN (SELECT 3 dia UNION SELECT 6) d
WHERE nome_feira = 'Feira de Pinheiros';

INSERT INTO tb_feira_dia (id_feira, dia_semana)
SELECT id_feira, d.dia FROM tb_feira
JOIN (SELECT 1 dia UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6 UNION SELECT 7) d
WHERE nome_feira = 'Feira Xepa Central';
