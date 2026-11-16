-- Leitura: a origem passa a ter três valores (REAL, SIMULADA, EXTERNA) e não pode
-- haver duas leituras do mesmo dispositivo no mesmo instante (data_hora em UTC).
ALTER TABLE leitura ADD COLUMN origem VARCHAR(20);
UPDATE leitura SET origem = CASE WHEN origem_simulada THEN 'SIMULADA' ELSE 'REAL' END;
ALTER TABLE leitura ALTER COLUMN origem SET NOT NULL;
ALTER TABLE leitura DROP COLUMN origem_simulada;

ALTER TABLE leitura
    ADD CONSTRAINT ck_leitura_origem CHECK (origem IN ('REAL', 'SIMULADA', 'EXTERNA')),
    ADD CONSTRAINT uq_leitura_dispositivo_data_hora UNIQUE (id_dispositivo, data_hora);

-- A restrição única já cria um índice por (id_dispositivo, data_hora).
DROP INDEX idx_leitura_dispositivo_data;

ALTER TABLE dispositivo
    ADD CONSTRAINT ck_dispositivo_status CHECK (status IN ('ATIVO', 'INATIVO', 'MANUTENCAO'));

ALTER TABLE sensor
    ADD CONSTRAINT ck_sensor_tipo CHECK (tipo_sensor IN (
        'TEMPERATURA_AR', 'UMIDADE_AR', 'UMIDADE_SOLO', 'LUMINOSIDADE',
        'PH_SOLO', 'VELOCIDADE_VENTO', 'PRECIPITACAO'
    ));
