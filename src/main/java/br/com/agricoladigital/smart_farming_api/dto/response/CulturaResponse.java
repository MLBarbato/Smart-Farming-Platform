package br.com.agricoladigital.smart_farming_api.dto.response;

public record CulturaResponse(
        Long id,
        String nome,
        Integer cicloDias
) {
}
