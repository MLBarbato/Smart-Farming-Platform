-- A estação meteorológica pertence à FAZENDA, não a um talhão. Todo dispositivo passa a ter
-- fazenda; o talhão torna-se opcional (sensores de talhão o informam, estações não).
ALTER TABLE dispositivo ADD COLUMN id_fazenda BIGINT;

UPDATE dispositivo d
   SET id_fazenda = t.id_fazenda
  FROM talhao t
 WHERE t.id_talhao = d.id_talhao;

ALTER TABLE dispositivo ALTER COLUMN id_fazenda SET NOT NULL;
ALTER TABLE dispositivo ALTER COLUMN id_talhao DROP NOT NULL;

ALTER TABLE dispositivo
    ADD CONSTRAINT fk_dispositivo_fazenda FOREIGN KEY (id_fazenda) REFERENCES fazenda (id_fazenda),
    ADD CONSTRAINT ck_dispositivo_tipo CHECK (tipo_dispositivo IN ('SENSOR', 'ESTACAO'));

-- Garante no banco que o talhão do dispositivo é da mesma fazenda do dispositivo
-- (sem talhão, a verificação é ignorada).
ALTER TABLE talhao ADD CONSTRAINT uq_talhao_id_fazenda UNIQUE (id_talhao, id_fazenda);
ALTER TABLE dispositivo
    ADD CONSTRAINT fk_dispositivo_talhao_da_fazenda
        FOREIGN KEY (id_talhao, id_fazenda) REFERENCES talhao (id_talhao, id_fazenda);

CREATE INDEX idx_dispositivo_fazenda ON dispositivo (id_fazenda);

-- No máximo uma estação por fazenda.
CREATE UNIQUE INDEX uq_dispositivo_estacao_por_fazenda
    ON dispositivo (id_fazenda) WHERE tipo_dispositivo = 'ESTACAO';

-- Estação: fonte dos dados e instante da última sincronização bem-sucedida (UTC).
ALTER TABLE estacao_meteorologica
    ADD COLUMN fonte VARCHAR(30) NOT NULL DEFAULT 'OPEN_METEO',
    ADD COLUMN ultima_sincronizacao TIMESTAMP;

ALTER TABLE estacao_meteorologica
    ADD CONSTRAINT ck_estacao_fonte CHECK (fonte IN ('OPEN_METEO')),
    ADD CONSTRAINT ck_estacao_frequencia CHECK (frequencia_coleta IS NULL OR frequencia_coleta > 0);

-- Sensores de uma estação (variáveis medidas pela estação, sem talhão).
ALTER TABLE sensor
    ADD COLUMN id_estacao BIGINT,
    ADD CONSTRAINT fk_sensor_estacao FOREIGN KEY (id_estacao)
        REFERENCES estacao_meteorologica (id_dispositivo);

CREATE INDEX idx_sensor_estacao ON sensor (id_estacao);
