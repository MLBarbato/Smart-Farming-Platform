-- Controle de concorrência otimista: o job de avaliação e o atendimento pelo usuário podem alterar
-- o mesmo alerta ao mesmo tempo; a segunda gravação falha em vez de sobrescrever a primeira.
ALTER TABLE alerta ADD COLUMN versao BIGINT NOT NULL DEFAULT 0;
