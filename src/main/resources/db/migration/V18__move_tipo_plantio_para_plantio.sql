ALTER TABLE plantio
    ADD COLUMN tipo_plantio VARCHAR(100);

-- Preserva o valor que estava no catálogo nos plantios já existentes.
UPDATE plantio
SET tipo_plantio = cultura.tipo_plantio
FROM cultura
WHERE plantio.id_cultura = cultura.id_cultura;

ALTER TABLE cultura
    DROP COLUMN tipo_plantio;
