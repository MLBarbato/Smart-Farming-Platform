package br.com.agricoladigital.smart_farming_api.client;

import br.com.agricoladigital.smart_farming_api.exception.PrevisaoIndisponivelException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenMeteoClientTest {
    private static final String URL = "https://api.exemplo.test/v1/forecast";

    private static final String JSON_VALIDO = """
            {"latitude":-17.8,"longitude":-50.9,"timezone":"America/Sao_Paulo","campo_novo":1,
             "daily_units":{"time":"iso8601","temperature_2m_mean":"°C","temperature_2m_min":"°C",
               "temperature_2m_max":"°C","precipitation_sum":"mm","precipitation_probability_max":"%"},
             "daily":{"time":["2026-11-16","2026-11-17"],
               "temperature_2m_mean":[24.5,25.1],"temperature_2m_min":[18.0,19.2],"temperature_2m_max":[31.0,32.4],
               "precipitation_sum":[0.0,12.6],"precipitation_probability_max":[10,85]}}""";

    private MockRestServiceServer servidor;
    private OpenMeteoClient cliente;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        cliente = new OpenMeteoClient(builder.build(), URL);
    }

    private void responder(String corpo) {
        servidor.expect(requestTo(org.hamcrest.Matchers.startsWith(URL)))
                .andRespond(withSuccess(corpo, MediaType.APPLICATION_JSON));
    }

    @Test
    void converteAResposta() {
        responder(JSON_VALIDO);

        List<DiaPrevisto> dias = cliente.buscarPrevisaoDiaria(-17.8, -50.9, 2);

        assertEquals(2, dias.size());
        assertEquals(new DiaPrevisto(LocalDate.of(2026, 11, 16), 24.5, 18.0, 31.0, 0.0, 10), dias.get(0));
        assertEquals(12.6, dias.get(1).precipitacaoMm());
        assertEquals(85, dias.get(1).probabilidadePrecipitacao());
    }

    @Test
    void enviaCoordenadasVariaveisDiariasFusoAutomaticoEDias() {
        servidor.expect(method(HttpMethod.GET))
                .andExpect(queryParam("latitude", "-17.8"))
                .andExpect(queryParam("longitude", "-50.9"))
                .andExpect(queryParam("timezone", "auto"))
                .andExpect(queryParam("forecast_days", "7"))
                .andExpect(queryParam("daily",
                        "temperature_2m_mean,temperature_2m_min,temperature_2m_max,precipitation_sum,precipitation_probability_max"))
                .andRespond(withSuccess(JSON_VALIDO, MediaType.APPLICATION_JSON));

        cliente.buscarPrevisaoDiaria(-17.8, -50.9, 7);

        servidor.verify();
    }

    @Test
    void valoresNulosDoProvedorSaoAceitos() {
        responder("""
                {"daily_units":{"temperature_2m_mean":"°C","temperature_2m_min":"°C","temperature_2m_max":"°C",
                   "precipitation_sum":"mm"},
                 "daily":{"time":["2026-11-16"],"temperature_2m_mean":[24.5],"temperature_2m_min":[18.0],
                   "temperature_2m_max":[31.0],"precipitation_sum":[0.0],"precipitation_probability_max":[null]}}""");

        assertNull(cliente.buscarPrevisaoDiaria(-17.8, -50.9, 1).get(0).probabilidadePrecipitacao());
    }

    @Test
    void erroHttpDoProvedorViraPrevisaoIndisponivel() {
        servidor.expect(requestTo(org.hamcrest.Matchers.startsWith(URL)))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":true,\"reason\":\"Latitude must be in range\"}"));

        assertThrows(PrevisaoIndisponivelException.class, () -> cliente.buscarPrevisaoDiaria(-17.8, -50.9, 7));
    }

    @Test
    void falhaDeRedeOuTimeoutViraPrevisaoIndisponivel() {
        servidor.expect(requestTo(org.hamcrest.Matchers.startsWith(URL)))
                .andRespond(request -> { throw new IOException("Read timed out"); });

        assertThrows(PrevisaoIndisponivelException.class, () -> cliente.buscarPrevisaoDiaria(-17.8, -50.9, 7));
    }

    @Test
    void respostaMalFormadaOuVaziaEhRejeitada() {
        responder("não é json");
        assertThrows(PrevisaoIndisponivelException.class, () -> cliente.buscarPrevisaoDiaria(-17.8, -50.9, 7));

        servidor.reset();
        responder("{}");
        assertThrows(PrevisaoIndisponivelException.class, () -> cliente.buscarPrevisaoDiaria(-17.8, -50.9, 7));
    }

    @Test
    void unidadesInesperadasSaoRejeitadas() {
        responder(JSON_VALIDO.replace("\"temperature_2m_max\":\"°C\"", "\"temperature_2m_max\":\"°F\""));

        assertThrows(PrevisaoIndisponivelException.class, () -> cliente.buscarPrevisaoDiaria(-17.8, -50.9, 2));
    }

    @Test
    void seriesComTamanhosDiferentesSaoRejeitadas() {
        responder(JSON_VALIDO.replace("\"precipitation_sum\":[0.0,12.6]", "\"precipitation_sum\":[0.0]"));

        assertThrows(PrevisaoIndisponivelException.class, () -> cliente.buscarPrevisaoDiaria(-17.8, -50.9, 2));
    }

    @Test
    void valoresForaDaFaixaPlausivelSaoRejeitados() {
        responder(JSON_VALIDO.replace("[24.5,25.1]", "[24.5,250.1]"));
        assertThrows(PrevisaoIndisponivelException.class, () -> cliente.buscarPrevisaoDiaria(-17.8, -50.9, 2));

        servidor.reset();
        responder(JSON_VALIDO.replace("[0.0,12.6]", "[0.0,-3.0]"));
        assertThrows(PrevisaoIndisponivelException.class, () -> cliente.buscarPrevisaoDiaria(-17.8, -50.9, 2));
    }

    @Test
    void dataInvalidaEhRejeitada() {
        responder(JSON_VALIDO.replace("2026-11-17", "ontem"));

        assertThrows(PrevisaoIndisponivelException.class, () -> cliente.buscarPrevisaoDiaria(-17.8, -50.9, 2));
    }

    @Test
    void diasForaDoLimiteSaoRejeitadosAntesDeChamarOProvedor() {
        assertThrows(IllegalArgumentException.class, () -> cliente.buscarPrevisaoDiaria(-17.8, -50.9, 0));
        assertThrows(IllegalArgumentException.class, () -> cliente.buscarPrevisaoDiaria(-17.8, -50.9, 17));
        servidor.verify();
    }

    private static final String JSON_HORARIO = """
            {"utc_offset_seconds":0,"timezone":"GMT",
             "hourly_units":{"time":"iso8601","temperature_2m":"°C","relative_humidity_2m":"%","wind_speed_10m":"km/h",
               "precipitation":"mm","soil_moisture_0_to_1cm":"m³/m³"},
             "hourly":{"time":["2026-11-16T11:00","2026-11-16T12:00"],
               "temperature_2m":[23.5,24.6],"relative_humidity_2m":[80,74],"wind_speed_10m":[8.1,8.7],
               "precipitation":[0.0,1.2],"soil_moisture_0_to_1cm":[0.447,0.443]}}""";

    @Test
    void converteLeiturasHorariasEmUtcComUmidadeDoSoloEmPercentual() {
        responder(JSON_HORARIO);

        List<MedicaoHoraria> medicoes = cliente.buscarMedicoesHorarias(-17.8, -50.9, 1);

        assertEquals(2, medicoes.size());
        MedicaoHoraria m = medicoes.get(1);
        assertEquals(java.time.LocalDateTime.of(2026, 11, 16, 12, 0), m.horaUtc());
        assertEquals(24.6, m.temperaturaCelsius());
        assertEquals(74.0, m.umidadeArPercentual());
        assertEquals(8.7, m.ventoKmPorHora());
        assertEquals(1.2, m.precipitacaoMm());
        assertEquals(44.3, m.umidadeSoloPercentual());
    }

    @Test
    void enviaParametrosHorarios() {
        servidor.expect(queryParam("hourly",
                        "temperature_2m,relative_humidity_2m,wind_speed_10m,precipitation,soil_moisture_0_to_1cm"))
                .andExpect(queryParam("past_hours", "72"))
                .andExpect(queryParam("forecast_hours", "1"))
                .andExpect(queryParam("timezone", "GMT"))
                .andRespond(withSuccess(JSON_HORARIO, MediaType.APPLICATION_JSON));

        cliente.buscarMedicoesHorarias(-17.8, -50.9, 72);

        servidor.verify();
    }

    @Test
    void valoresHorariosNulosSaoMantidosComoNulos() {
        responder(JSON_HORARIO.replace("[0.447,0.443]", "[null,0.443]"));

        assertNull(cliente.buscarMedicoesHorarias(-17.8, -50.9, 1).get(0).umidadeSoloPercentual());
    }

    @Test
    void respostaHorariaInvalidaEhRejeitada() {
        responder(JSON_HORARIO.replace("\"utc_offset_seconds\":0", "\"utc_offset_seconds\":-10800"));
        assertThrows(PrevisaoIndisponivelException.class, () -> cliente.buscarMedicoesHorarias(-17.8, -50.9, 1));

        servidor.reset();
        responder(JSON_HORARIO.replace("\"m³/m³\"", "\"%\""));
        assertThrows(PrevisaoIndisponivelException.class, () -> cliente.buscarMedicoesHorarias(-17.8, -50.9, 1));

        servidor.reset();
        responder(JSON_HORARIO.replace("[80,74]", "[80,174]"));
        assertThrows(PrevisaoIndisponivelException.class, () -> cliente.buscarMedicoesHorarias(-17.8, -50.9, 1));

        servidor.reset();
        responder(JSON_HORARIO.replace("[0.0,1.2]", "[0.0]"));
        assertThrows(PrevisaoIndisponivelException.class, () -> cliente.buscarMedicoesHorarias(-17.8, -50.9, 1));

        servidor.reset();
        responder("{}");
        assertThrows(PrevisaoIndisponivelException.class, () -> cliente.buscarMedicoesHorarias(-17.8, -50.9, 1));
    }

    @Test
    void erroHttpNasLeiturasHorariasViraIndisponivel() {
        servidor.expect(requestTo(org.hamcrest.Matchers.startsWith(URL))).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThrows(PrevisaoIndisponivelException.class, () -> cliente.buscarMedicoesHorarias(-17.8, -50.9, 1));
    }

    @Test
    void horasPassadasForaDoLimiteSaoRejeitadas() {
        assertThrows(IllegalArgumentException.class, () -> cliente.buscarMedicoesHorarias(-17.8, -50.9, -1));
        assertThrows(IllegalArgumentException.class,
                () -> cliente.buscarMedicoesHorarias(-17.8, -50.9, OpenMeteoClient.HORAS_PASSADAS_MAXIMAS + 1));
    }
}
