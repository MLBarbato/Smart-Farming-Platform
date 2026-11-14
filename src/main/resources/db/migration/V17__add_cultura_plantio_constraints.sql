CREATE UNIQUE INDEX uk_cultura_nome
    ON cultura (LOWER(nome));

ALTER TABLE cultura
    ADD CONSTRAINT ck_cultura_ciclo_positivo
        CHECK (ciclo_dias IS NULL OR ciclo_dias > 0);

ALTER TABLE plantio
    ADD CONSTRAINT ck_plantio_datas
        CHECK (data_colheita_prevista IS NULL OR data_colheita_prevista >= data_plantio);

ALTER TABLE plantio
    ADD CONSTRAINT ck_plantio_status
        CHECK (status IN ('PLANEJADO', 'EM_CULTIVO', 'COLHIDO', 'CANCELADO'));

CREATE INDEX idx_plantio_talhao_status
    ON plantio (id_talhao, status);

-- Um talhão só pode ter um plantio em cultivo por vez.
CREATE UNIQUE INDEX uk_plantio_talhao_em_cultivo
    ON plantio (id_talhao)
    WHERE status = 'EM_CULTIVO';
