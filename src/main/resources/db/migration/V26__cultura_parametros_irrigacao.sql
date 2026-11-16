-- Parâmetros opcionais usados na recomendação de irrigação (balanço pela umidade do solo).
-- Valores de demonstração definidos pelo administrador; culturas sem eles ficam com
-- recomendação "dados insuficientes".
ALTER TABLE cultura
    ADD COLUMN umidade_minima DOUBLE PRECISION,
    ADD COLUMN umidade_ideal DOUBLE PRECISION,
    ADD COLUMN profundidade_raiz_cm INTEGER;

ALTER TABLE cultura
    ADD CONSTRAINT ck_cultura_irrigacao_completa CHECK (
        (umidade_minima IS NULL AND umidade_ideal IS NULL AND profundidade_raiz_cm IS NULL)
        OR (umidade_minima IS NOT NULL AND umidade_ideal IS NOT NULL AND profundidade_raiz_cm IS NOT NULL)),
    ADD CONSTRAINT ck_cultura_umidades CHECK (
        umidade_minima IS NULL OR (umidade_minima > 0 AND umidade_minima < umidade_ideal AND umidade_ideal <= 100)),
    ADD CONSTRAINT ck_cultura_profundidade_raiz CHECK (
        profundidade_raiz_cm IS NULL OR (profundidade_raiz_cm > 0 AND profundidade_raiz_cm <= 300));
