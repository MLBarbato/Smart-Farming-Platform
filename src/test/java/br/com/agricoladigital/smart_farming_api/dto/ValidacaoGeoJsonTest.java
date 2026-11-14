package br.com.agricoladigital.smart_farming_api.dto;

import br.com.agricoladigital.smart_farming_api.dto.request.FazendaRequest;
import br.com.agricoladigital.smart_farming_api.dto.request.TalhaoRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidacaoGeoJsonTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private int violacoesTalhao(String geometria) {
        var node = geometria == null ? null : JSON.readTree(geometria);
        return validator.validate(new TalhaoRequest("T-01", 10.0, node)).size();
    }

    private int violacoesFazenda(Double lat, Double lon, String estado) {
        return validator.validate(new FazendaRequest("F", null, 10.0, lat, lon, null, estado)).size();
    }

    @Test
    void aceitaPoligonoFechado() {
        assertEquals(0, violacoesTalhao("""
                {"type":"Polygon","coordinates":[[[-50.9,-17.8],[-50.8,-17.8],[-50.8,-17.7],[-50.9,-17.8]]]}"""));
    }

    @Test
    void aceitaGeometriaAusente() {
        assertEquals(0, violacoesTalhao(null));
    }

    @Test
    void rejeitaTextoLivreETipoDiferenteDePolygon() {
        assertEquals(1, violacoesTalhao("\"polígono qualquer\""));
        assertEquals(1, violacoesTalhao("{\"type\":\"Point\",\"coordinates\":[-50.9,-17.8]}"));
    }

    @Test
    void rejeitaAnelAbertoOuCurto() {
        assertEquals(1, violacoesTalhao("""
                {"type":"Polygon","coordinates":[[[-50.9,-17.8],[-50.8,-17.8],[-50.8,-17.7],[-50.7,-17.7]]]}"""));
        assertEquals(1, violacoesTalhao("""
                {"type":"Polygon","coordinates":[[[-50.9,-17.8],[-50.8,-17.8],[-50.9,-17.8]]]}"""));
    }

    @Test
    void rejeitaCoordenadasForaDoIntervaloOuInvertidas() {
        // latitude 200 (provável inversão lat/lon)
        assertEquals(1, violacoesTalhao("""
                {"type":"Polygon","coordinates":[[[-50.9,200],[-50.8,-17.8],[-50.8,-17.7],[-50.9,200]]]}"""));
    }

    @Test
    void rejeitaPosicaoNaoNumerica() {
        assertEquals(1, violacoesTalhao("""
                {"type":"Polygon","coordinates":[[["a","b"],[-50.8,-17.8],[-50.8,-17.7],["a","b"]]]}"""));
    }

    @Test
    void fazendaExigeLatitudeELongitudeJuntas() {
        assertEquals(0, violacoesFazenda(-17.8, -50.9, "GO"));
        assertEquals(0, violacoesFazenda(null, null, null));
        assertTrue(violacoesFazenda(-17.8, null, null) > 0);
        assertTrue(violacoesFazenda(null, -50.9, null) > 0);
    }

    @Test
    void fazendaRejeitaCoordenadasForaDoIntervaloEEstadoInvalido() {
        assertTrue(violacoesFazenda(91.0, -50.9, null) > 0);
        assertTrue(violacoesFazenda(-17.8, 181.0, null) > 0);
        assertTrue(violacoesFazenda(-17.8, -50.9, "Goiás") > 0);
    }
}
