-- Previsão diária por fazenda: guarda mínima, máxima, probabilidade de chuva e o instante da
-- consulta ao provedor (UTC), para informar a idade dos dados. Há no máximo uma linha por
-- fazenda e dia: cada atualização substitui o conjunto anterior.
ALTER TABLE previsao_tempo
    ADD COLUMN temperatura_minima DOUBLE PRECISION,
    ADD COLUMN temperatura_maxima DOUBLE PRECISION,
    ADD COLUMN probabilidade_precipitacao INTEGER,
    ADD COLUMN consultada_em TIMESTAMP;

UPDATE previsao_tempo SET consultada_em = (now() AT TIME ZONE 'UTC') WHERE consultada_em IS NULL;
ALTER TABLE previsao_tempo ALTER COLUMN consultada_em SET NOT NULL;

ALTER TABLE previsao_tempo
    ADD CONSTRAINT ck_previsao_precipitacao CHECK (precipitacao_prevista IS NULL OR precipitacao_prevista >= 0),
    ADD CONSTRAINT ck_previsao_probabilidade
        CHECK (probabilidade_precipitacao IS NULL OR probabilidade_precipitacao BETWEEN 0 AND 100),
    ADD CONSTRAINT uq_previsao_fazenda_data UNIQUE (id_fazenda, data_previsao);

-- A restrição única já cria um índice por (id_fazenda, data_previsao).
DROP INDEX idx_previsao_fazenda_data;
