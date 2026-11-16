-- Regras de alerta: uma linha por evento e criticidade, com operador, limiar e janela.
-- As tabelas nunca tiveram API, então não há dados a preservar; se houvesse linhas, as
-- colunas NOT NULL abaixo falhariam e a migração pararia, em vez de descartar dados.
ALTER TABLE regra_alerta RENAME COLUMN condicao_limite TO limiar;
ALTER TABLE regra_alerta DROP COLUMN tipo_evento;

ALTER TABLE regra_alerta
    ADD COLUMN evento VARCHAR(40) NOT NULL,
    ADD COLUMN criticidade VARCHAR(10) NOT NULL,
    ADD COLUMN operador VARCHAR(20) NOT NULL,
    ADD COLUMN variavel VARCHAR(30),
    ADD COLUMN unidade VARCHAR(20) NOT NULL,
    ADD COLUMN janela_horas INTEGER,
    ADD COLUMN ativa BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE regra_alerta
    ADD CONSTRAINT ck_regra_evento
        CHECK (evento IN ('SECA', 'EXCESSO_PRECIPITACAO', 'RISCO_GEADA', 'FALHA_SENSOR')),
    ADD CONSTRAINT ck_regra_criticidade CHECK (criticidade IN ('BAIXA', 'MEDIA', 'ALTA')),
    ADD CONSTRAINT ck_regra_operador
        CHECK (operador IN ('MENOR_QUE', 'MENOR_OU_IGUAL', 'MAIOR_QUE', 'MAIOR_OU_IGUAL')),
    ADD CONSTRAINT ck_regra_variavel CHECK (variavel IS NULL OR variavel IN (
        'TEMPERATURA_AR', 'UMIDADE_AR', 'UMIDADE_SOLO', 'LUMINOSIDADE',
        'PH_SOLO', 'VELOCIDADE_VENTO', 'PRECIPITACAO')),
    ADD CONSTRAINT ck_regra_janela CHECK (janela_horas IS NULL OR janela_horas BETWEEN 1 AND 168),
    ADD CONSTRAINT uq_regra_evento_criticidade UNIQUE (evento, criticidade);

INSERT INTO regra_alerta (evento, criticidade, operador, variavel, limiar, unidade, janela_horas, descricao) VALUES
    ('SECA', 'BAIXA', 'MENOR_QUE', 'UMIDADE_SOLO', 30, '%', 3,
        'Umidade do solo média das últimas 3 horas abaixo de 30%'),
    ('SECA', 'MEDIA', 'MENOR_QUE', 'UMIDADE_SOLO', 25, '%', 3,
        'Umidade do solo média das últimas 3 horas abaixo de 25%'),
    ('SECA', 'ALTA', 'MENOR_QUE', 'UMIDADE_SOLO', 20, '%', 3,
        'Umidade do solo média das últimas 3 horas abaixo de 20%'),
    ('EXCESSO_PRECIPITACAO', 'BAIXA', 'MAIOR_OU_IGUAL', 'PRECIPITACAO', 30, 'mm', 24,
        'Chuva acumulada (ou prevista) em 24 horas de 30 mm ou mais'),
    ('EXCESSO_PRECIPITACAO', 'MEDIA', 'MAIOR_OU_IGUAL', 'PRECIPITACAO', 50, 'mm', 24,
        'Chuva acumulada (ou prevista) em 24 horas de 50 mm ou mais'),
    ('EXCESSO_PRECIPITACAO', 'ALTA', 'MAIOR_OU_IGUAL', 'PRECIPITACAO', 100, 'mm', 24,
        'Chuva acumulada (ou prevista) em 24 horas de 100 mm ou mais'),
    ('RISCO_GEADA', 'BAIXA', 'MENOR_OU_IGUAL', 'TEMPERATURA_AR', 5, '°C', 72,
        'Temperatura mínima prevista nos próximos 3 dias de 5 °C ou menos'),
    ('RISCO_GEADA', 'MEDIA', 'MENOR_OU_IGUAL', 'TEMPERATURA_AR', 3, '°C', 72,
        'Temperatura mínima prevista nos próximos 3 dias de 3 °C ou menos'),
    ('RISCO_GEADA', 'ALTA', 'MENOR_OU_IGUAL', 'TEMPERATURA_AR', 1, '°C', 72,
        'Temperatura mínima prevista nos próximos 3 dias de 1 °C ou menos'),
    ('FALHA_SENSOR', 'MEDIA', 'MAIOR_QUE', NULL, 30, 'min', NULL,
        'Sensor ativo sem leitura há mais de 30 minutos'),
    ('FALHA_SENSOR', 'ALTA', 'MAIOR_QUE', NULL, 360, 'min', NULL,
        'Sensor ativo sem leitura há mais de 6 horas');

-- Alertas: estados e criticidade passam a ser enums validados no banco, com os campos do
-- atendimento. Um alerta fechado tem sempre data de fechamento, e só pode haver um alerta
-- aberto (ABERTO ou EM_ATENDIMENTO) por talhão e evento.
ALTER TABLE alerta RENAME COLUMN nivel_severidade TO criticidade;
ALTER TABLE alerta DROP COLUMN tipo;

ALTER TABLE alerta
    ADD COLUMN evento VARCHAR(40) NOT NULL,
    ADD COLUMN id_atendente BIGINT,
    ADD COLUMN atendido_em TIMESTAMP,
    ADD COLUMN resolvido_em TIMESTAMP,
    ADD COLUMN resolucao VARCHAR(20),
    ADD COLUMN nota VARCHAR(500),
    ADD COLUMN normalizado_em TIMESTAMP,
    ADD COLUMN atualizado_em TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'UTC');

ALTER TABLE alerta
    ADD CONSTRAINT fk_alerta_atendente FOREIGN KEY (id_atendente) REFERENCES usuario (id_usuario),
    ADD CONSTRAINT ck_alerta_evento
        CHECK (evento IN ('SECA', 'EXCESSO_PRECIPITACAO', 'RISCO_GEADA', 'FALHA_SENSOR')),
    ADD CONSTRAINT ck_alerta_criticidade CHECK (criticidade IN ('BAIXA', 'MEDIA', 'ALTA')),
    ADD CONSTRAINT ck_alerta_status CHECK (status IN ('ABERTO', 'EM_ATENDIMENTO', 'RESOLVIDO', 'DISPENSADO')),
    ADD CONSTRAINT ck_alerta_resolucao CHECK (
        resolucao IS NULL OR (resolucao IN ('MANUAL', 'AUTOMATICA') AND status = 'RESOLVIDO')),
    ADD CONSTRAINT ck_alerta_fechamento CHECK (
        (status IN ('RESOLVIDO', 'DISPENSADO')) = (resolvido_em IS NOT NULL));

CREATE UNIQUE INDEX uq_alerta_aberto_por_talhao_evento
    ON alerta (id_talhao, evento) WHERE status IN ('ABERTO', 'EM_ATENDIMENTO');

CREATE INDEX idx_alerta_talhao_status ON alerta (id_talhao, status);
