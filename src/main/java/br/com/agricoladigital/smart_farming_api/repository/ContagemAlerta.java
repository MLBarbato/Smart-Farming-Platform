package br.com.agricoladigital.smart_farming_api.repository;

import br.com.agricoladigital.smart_farming_api.entity.CriticidadeAlerta;

/** Quantidade de alertas abertos de um talhão em uma criticidade. */
public record ContagemAlerta(Long fazendaId, Long talhaoId, CriticidadeAlerta criticidade, long total) {
}
