ALTER TABLE plantio
    ADD COLUMN safra VARCHAR(9),
    ADD COLUMN data_colheita_real DATE,
    ADD COLUMN quantidade_colhida_kg DOUBLE PRECISION;

-- Ano-safra iniciado em julho: plantio em set/2026 ou mar/2027 => 2026/2027.
UPDATE plantio
SET safra = CASE
    WHEN EXTRACT(MONTH FROM data_plantio) >= 7
        THEN EXTRACT(YEAR FROM data_plantio)::INT || '/' || (EXTRACT(YEAR FROM data_plantio)::INT + 1)
    ELSE (EXTRACT(YEAR FROM data_plantio)::INT - 1) || '/' || EXTRACT(YEAR FROM data_plantio)::INT
END;

-- Plantios já colhidos antes deste campo existir: assume a data prevista (ou a de plantio).
UPDATE plantio
SET data_colheita_real = COALESCE(data_colheita_prevista, data_plantio)
WHERE status = 'COLHIDO' AND data_colheita_real IS NULL;

ALTER TABLE plantio
    ALTER COLUMN safra SET NOT NULL;

ALTER TABLE plantio
    ADD CONSTRAINT ck_plantio_safra_formato
        CHECK (safra ~ '^[0-9]{4}/[0-9]{4}$'),
    ADD CONSTRAINT ck_plantio_colheita_real_data
        CHECK (data_colheita_real IS NULL OR data_colheita_real >= data_plantio),
    ADD CONSTRAINT ck_plantio_quantidade_positiva
        CHECK (quantidade_colhida_kg IS NULL OR quantidade_colhida_kg > 0),
    ADD CONSTRAINT ck_plantio_colheita_somente_colhido
        CHECK (status = 'COLHIDO' OR (data_colheita_real IS NULL AND quantidade_colhida_kg IS NULL)),
    ADD CONSTRAINT ck_plantio_colhido_tem_data
        CHECK (status <> 'COLHIDO' OR data_colheita_real IS NOT NULL);

CREATE INDEX idx_plantio_talhao_safra
    ON plantio (id_talhao, safra);
