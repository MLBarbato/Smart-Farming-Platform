-- Novas variáveis monitoradas: temperatura do solo e condutividade elétrica do solo.
ALTER TABLE sensor DROP CONSTRAINT ck_sensor_tipo;
ALTER TABLE sensor
    ADD CONSTRAINT ck_sensor_tipo CHECK (tipo_sensor IN (
        'TEMPERATURA_AR', 'UMIDADE_AR', 'UMIDADE_SOLO', 'LUMINOSIDADE',
        'PH_SOLO', 'VELOCIDADE_VENTO', 'PRECIPITACAO', 'TEMPERATURA_SOLO', 'CONDUTIVIDADE_ELETRICA'
    ));
