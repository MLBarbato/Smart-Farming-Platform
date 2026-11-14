package br.com.agricoladigital.smart_farming_api.dto.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import tools.jackson.databind.JsonNode;

public class PoligonoGeoJsonValidator implements ConstraintValidator<PoligonoGeoJson, JsonNode> {
    static final int MAXIMO_POSICOES = 1000;

    @Override
    public boolean isValid(JsonNode geometria, ConstraintValidatorContext context) {
        if (geometria == null || geometria.isNull()) {
            return true;
        }
        String erro = verificar(geometria);
        if (erro == null) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate("geometria inválida: " + erro)
                .addConstraintViolation();
        return false;
    }

    /** Retorna o motivo da invalidade, ou {@code null} quando o polígono é válido. */
    static String verificar(JsonNode geometria) {
        if (!geometria.isObject()) {
            return "esperado um objeto GeoJSON";
        }
        JsonNode tipo = geometria.get("type");
        if (tipo == null || !tipo.isString() || !"Polygon".equals(tipo.asString())) {
            return "o tipo deve ser \"Polygon\"";
        }
        JsonNode aneis = geometria.get("coordinates");
        if (aneis == null || !aneis.isArray() || aneis.isEmpty()) {
            return "coordinates deve ser uma lista de anéis";
        }
        int posicoes = 0;
        for (JsonNode anel : aneis) {
            if (!anel.isArray() || anel.size() < 4) {
                return "cada anel precisa de ao menos 4 posições";
            }
            posicoes += anel.size();
            if (posicoes > MAXIMO_POSICOES) {
                return "o polígono excede " + MAXIMO_POSICOES + " posições";
            }
            for (JsonNode posicao : anel) {
                String erro = verificarPosicao(posicao);
                if (erro != null) {
                    return erro;
                }
            }
            if (!anel.get(0).equals(anel.get(anel.size() - 1))) {
                return "cada anel deve ser fechado (primeira e última posição iguais)";
            }
        }
        return null;
    }

    private static String verificarPosicao(JsonNode posicao) {
        if (!posicao.isArray() || posicao.size() < 2 || posicao.size() > 3) {
            return "cada posição deve ser [longitude, latitude]";
        }
        for (JsonNode valor : posicao) {
            if (!valor.isNumber() || !Double.isFinite(valor.doubleValue())) {
                return "as coordenadas devem ser números";
            }
        }
        double longitude = posicao.get(0).doubleValue();
        double latitude = posicao.get(1).doubleValue();
        if (longitude < -180 || longitude > 180 || latitude < -90 || latitude > 90) {
            return "posição fora do intervalo válido (longitude -180 a 180, latitude -90 a 90)";
        }
        return null;
    }
}
