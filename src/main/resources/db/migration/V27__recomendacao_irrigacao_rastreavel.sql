-- Recomendação de irrigação com resultado explícito e insumos do cálculo gravados.
-- A previsão do provedor é substituída a cada atualização, então a recomendação guarda uma cópia
-- dos valores usados; o vínculo com a linha de previsão passa a ser apenas uma referência opcional.
ALTER TABLE recomendacao_irrigacao DROP CONSTRAINT fk_recomendacao_previsao;
ALTER TABLE recomendacao_irrigacao
    ADD CONSTRAINT fk_recomendacao_previsao FOREIGN KEY (id_previsao)
        REFERENCES previsao_tempo (id_previsao) ON DELETE SET NULL;

ALTER TABLE recomendacao_irrigacao ALTER COLUMN volume_recomendado_mm DROP NOT NULL;

ALTER TABLE recomendacao_irrigacao
    ADD COLUMN situacao VARCHAR(30) NOT NULL DEFAULT 'SEM_DADOS_SUFICIENTES',
    ADD COLUMN valido_ate TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),
    ADD COLUMN cultura_nome VARCHAR(100),
    ADD COLUMN umidade_minima DOUBLE PRECISION,
    ADD COLUMN umidade_ideal DOUBLE PRECISION,
    ADD COLUMN profundidade_raiz_cm INTEGER,
    ADD COLUMN umidade_atual DOUBLE PRECISION,
    ADD COLUMN origem_umidade VARCHAR(30),
    ADD COLUMN deficit_mm DOUBLE PRECISION,
    ADD COLUMN chuva_prevista_mm DOUBLE PRECISION,
    ADD COLUMN chuva_efetiva_mm DOUBLE PRECISION,
    ADD COLUMN temperatura_maxima_prevista DOUBLE PRECISION,
    ADD COLUMN ajuste_temperatura BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN previsao_consultada_em TIMESTAMP,
    ADD COLUMN previsao_fonte VARCHAR(100),
    ADD COLUMN dados_faltantes VARCHAR(255);

ALTER TABLE recomendacao_irrigacao ALTER COLUMN situacao DROP DEFAULT;
ALTER TABLE recomendacao_irrigacao ALTER COLUMN valido_ate DROP DEFAULT;

ALTER TABLE recomendacao_irrigacao
    ADD CONSTRAINT ck_recomendacao_situacao
        CHECK (situacao IN ('IRRIGAR', 'NAO_IRRIGAR', 'AGUARDAR_CHUVA', 'SEM_DADOS_SUFICIENTES')),
    ADD CONSTRAINT ck_recomendacao_volume CHECK (
        (situacao = 'IRRIGAR' AND volume_recomendado_mm IS NOT NULL AND volume_recomendado_mm > 0)
        OR (situacao <> 'IRRIGAR' AND volume_recomendado_mm IS NULL)),
    ADD CONSTRAINT ck_recomendacao_origem_umidade
        CHECK (origem_umidade IS NULL OR origem_umidade IN ('SENSOR_TALHAO', 'ESTACAO'));

CREATE INDEX idx_recomendacao_talhao_data ON recomendacao_irrigacao (id_talhao, data_hora DESC);
