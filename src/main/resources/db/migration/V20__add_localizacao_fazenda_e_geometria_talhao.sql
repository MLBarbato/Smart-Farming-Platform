-- Fazenda: coordenadas e localidade, usadas pelo mapa e pela consulta de previsão.
-- Opcionais para preservar fazendas já cadastradas; latitude e longitude andam juntas.
ALTER TABLE fazenda
    ADD COLUMN latitude DOUBLE PRECISION,
    ADD COLUMN longitude DOUBLE PRECISION,
    ADD COLUMN cidade VARCHAR(100),
    ADD COLUMN estado VARCHAR(2);

ALTER TABLE fazenda
    ADD CONSTRAINT ck_fazenda_latitude CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    ADD CONSTRAINT ck_fazenda_longitude CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
    ADD CONSTRAINT ck_fazenda_coordenadas_juntas CHECK ((latitude IS NULL) = (longitude IS NULL));

-- Talhão: a geometria passa a ser um GeoJSON do tipo Polygon (JSONB).
-- Os valores antigos eram texto livre, nunca validados como geometria, e não
-- podem ser convertidos; por isso são descartados (viram NULL).
ALTER TABLE talhao
    ALTER COLUMN geometria TYPE JSONB USING NULL;

ALTER TABLE talhao
    ADD CONSTRAINT ck_talhao_geometria_poligono CHECK (
        geometria IS NULL
        OR (jsonb_typeof(geometria) = 'object' AND geometria ->> 'type' = 'Polygon')
    );
