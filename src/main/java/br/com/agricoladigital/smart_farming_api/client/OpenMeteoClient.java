package br.com.agricoladigital.smart_farming_api.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import br.com.agricoladigital.smart_farming_api.exception.PrevisaoIndisponivelException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import br.com.agricoladigital.smart_farming_api.entity.VariavelMonitorada;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Previsão diária pela API pública do Open-Meteo (uso gratuito: apenas não comercial, licença
 * CC BY 4.0). Valida a resposta antes de entregá-la: tamanhos, unidades e faixas plausíveis.
 */
@Component
public class OpenMeteoClient implements ClimaClient {
    public static final String FONTE = "OPEN_METEO";
    static final int DIAS_MAXIMOS = 16;
    /** O provedor aceita até 92 dias de passado. */
    static final int HORAS_PASSADAS_MAXIMAS = 92 * 24;

    private static final String VARIAVEIS_DIARIAS =
            "temperature_2m_mean,temperature_2m_min,temperature_2m_max,precipitation_sum,precipitation_probability_max";

    private static final String VARIAVEIS_HORARIAS =
            "temperature_2m,relative_humidity_2m,wind_speed_10m,precipitation,soil_moisture_0_to_1cm";

    private final RestClient restClient;
    private final String url;

    @Autowired
    public OpenMeteoClient(
            @Value("${app.previsao.url:https://api.open-meteo.com/v1/forecast}") String url,
            @Value("${app.previsao.timeout-segundos:10}") long timeoutSegundos
    ) {
        if (timeoutSegundos < 1) {
            throw new IllegalArgumentException("app.previsao.timeout-segundos deve ser positivo.");
        }
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(timeoutSegundos));
        fabrica.setReadTimeout(Duration.ofSeconds(timeoutSegundos));
        this.restClient = RestClient.builder().requestFactory(fabrica).build();
        this.url = url;
    }

    /** Para testes: usa um {@link RestClient} já montado. */
    OpenMeteoClient(RestClient restClient, String url) {
        this.restClient = restClient;
        this.url = url;
    }

    @Override
    public String fonte() {
        return FONTE;
    }

    @Override
    public List<DiaPrevisto> buscarPrevisaoDiaria(double latitude, double longitude, int dias) {
        if (dias < 1 || dias > DIAS_MAXIMOS) {
            throw new IllegalArgumentException("dias deve estar entre 1 e " + DIAS_MAXIMOS);
        }
        URI uri = UriComponentsBuilder.fromUriString(url)
                .queryParam("latitude", latitude)
                .queryParam("longitude", longitude)
                .queryParam("daily", VARIAVEIS_DIARIAS)
                .queryParam("timezone", "auto")
                .queryParam("forecast_days", dias)
                .build().toUri();
        Resposta resposta;
        try {
            resposta = restClient.get().uri(uri).retrieve().body(Resposta.class);
        } catch (RestClientException erro) {
            throw new PrevisaoIndisponivelException("Falha ao consultar o provedor de previsão.", erro);
        }
        return converter(resposta);
    }

    @Override
    public List<MedicaoHoraria> buscarMedicoesHorarias(double latitude, double longitude, int horasPassadas) {
        if (horasPassadas < 0 || horasPassadas > HORAS_PASSADAS_MAXIMAS) {
            throw new IllegalArgumentException("horasPassadas deve estar entre 0 e " + HORAS_PASSADAS_MAXIMAS);
        }
        URI uri = UriComponentsBuilder.fromUriString(url)
                .queryParam("latitude", latitude)
                .queryParam("longitude", longitude)
                .queryParam("hourly", VARIAVEIS_HORARIAS)
                .queryParam("past_hours", horasPassadas)
                .queryParam("forecast_hours", 1)
                .queryParam("timezone", "GMT")
                .build().toUri();
        RespostaHoraria resposta;
        try {
            resposta = restClient.get().uri(uri).retrieve().body(RespostaHoraria.class);
        } catch (RestClientException erro) {
            throw new PrevisaoIndisponivelException("Falha ao consultar o provedor de clima.", erro);
        }
        return converterHorarias(resposta);
    }

    static List<MedicaoHoraria> converterHorarias(RespostaHoraria resposta) {
        if (resposta == null || resposta.hourly() == null || resposta.hourly().time() == null
                || resposta.hourly().time().isEmpty()) {
            throw invalida("resposta sem dados horários");
        }
        if (resposta.utcOffsetSeconds() == null || resposta.utcOffsetSeconds() != 0) {
            throw invalida("os horários não vieram em UTC");
        }
        Horario h = resposta.hourly();
        UnidadesHorarias u = resposta.hourlyUnits();
        if (u == null || !"°C".equals(u.temperatura()) || !"%".equals(u.umidadeAr()) || !"km/h".equals(u.vento())
                || !"mm".equals(u.precipitacao()) || !"m³/m³".equals(u.umidadeSolo())) {
            throw invalida("unidades inesperadas");
        }
        int n = h.time().size();
        for (List<?> serie : List.of(orVazia(h.temperatura()), orVazia(h.umidadeAr()), orVazia(h.vento()),
                orVazia(h.precipitacao()), orVazia(h.umidadeSolo()))) {
            if (serie.size() != n) {
                throw invalida("séries com tamanhos diferentes");
            }
        }
        List<MedicaoHoraria> medicoes = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            java.time.LocalDateTime hora;
            try {
                hora = java.time.LocalDateTime.parse(h.time().get(i));
            } catch (DateTimeParseException erro) {
                throw invalida("data/hora inválida: " + h.time().get(i));
            }
            Double solo = h.umidadeSolo().get(i) == null ? null : Math.round(h.umidadeSolo().get(i) * 10_000) / 100.0;
            verificar("temperatura", h.temperatura().get(i), VariavelMonitorada.TEMPERATURA_AR);
            verificar("umidade do ar", h.umidadeAr().get(i), VariavelMonitorada.UMIDADE_AR);
            verificar("vento", h.vento().get(i), VariavelMonitorada.VELOCIDADE_VENTO);
            verificar("precipitação", h.precipitacao().get(i), VariavelMonitorada.PRECIPITACAO);
            verificar("umidade do solo", solo, VariavelMonitorada.UMIDADE_SOLO);
            medicoes.add(new MedicaoHoraria(
                    hora, h.temperatura().get(i), h.umidadeAr().get(i), h.vento().get(i),
                    h.precipitacao().get(i), solo));
        }
        return medicoes;
    }

    private static void verificar(String nome, Double valor, VariavelMonitorada variavel) {
        if (valor != null && !variavel.aceita(valor)) {
            throw invalida(nome + " fora da faixa plausível: " + valor);
        }
    }

    static List<DiaPrevisto> converter(Resposta resposta) {
        if (resposta == null || resposta.daily() == null || resposta.daily().time() == null
                || resposta.daily().time().isEmpty()) {
            throw invalida("resposta sem dados diários");
        }
        Diario d = resposta.daily();
        Unidades u = resposta.dailyUnits();
        if (u == null || !"°C".equals(u.temperaturaMaxima()) || !"°C".equals(u.temperaturaMinima())
                || !"°C".equals(u.temperaturaMedia()) || !"mm".equals(u.precipitacao())) {
            throw invalida("unidades inesperadas");
        }
        int n = d.time().size();
        for (List<?> serie : List.of(
                orVazia(d.temperaturaMedia()), orVazia(d.temperaturaMinima()), orVazia(d.temperaturaMaxima()),
                orVazia(d.precipitacao()), orVazia(d.probabilidade()))) {
            if (serie.size() != n) {
                throw invalida("séries com tamanhos diferentes");
            }
        }
        List<DiaPrevisto> dias = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            LocalDate data;
            try {
                data = LocalDate.parse(d.time().get(i));
            } catch (DateTimeParseException erro) {
                throw invalida("data inválida: " + d.time().get(i));
            }
            Double media = d.temperaturaMedia().get(i);
            Double minima = d.temperaturaMinima().get(i);
            Double maxima = d.temperaturaMaxima().get(i);
            Double chuva = d.precipitacao().get(i);
            Double probabilidade = d.probabilidade().get(i);
            verificarFaixa("temperatura", media, -90, 60);
            verificarFaixa("temperatura mínima", minima, -90, 60);
            verificarFaixa("temperatura máxima", maxima, -90, 60);
            verificarFaixa("precipitação", chuva, 0, 2000);
            verificarFaixa("probabilidade", probabilidade, 0, 100);
            dias.add(new DiaPrevisto(
                    data, media, minima, maxima, chuva,
                    probabilidade == null ? null : (int) Math.round(probabilidade)
            ));
        }
        return dias;
    }

    private static <T> List<T> orVazia(List<T> lista) {
        return lista == null ? List.of() : lista;
    }

    private static void verificarFaixa(String nome, Double valor, double minimo, double maximo) {
        if (valor != null && (!Double.isFinite(valor) || valor < minimo || valor > maximo)) {
            throw invalida(nome + " fora da faixa plausível: " + valor);
        }
    }

    private static PrevisaoIndisponivelException invalida(String motivo) {
        return new PrevisaoIndisponivelException("O provedor de previsão devolveu dados inválidos: " + motivo + ".");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Resposta(Diario daily, @JsonProperty("daily_units") Unidades dailyUnits) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Diario(
            List<String> time,
            @JsonProperty("temperature_2m_mean") List<Double> temperaturaMedia,
            @JsonProperty("temperature_2m_min") List<Double> temperaturaMinima,
            @JsonProperty("temperature_2m_max") List<Double> temperaturaMaxima,
            @JsonProperty("precipitation_sum") List<Double> precipitacao,
            @JsonProperty("precipitation_probability_max") List<Double> probabilidade
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Unidades(
            @JsonProperty("temperature_2m_mean") String temperaturaMedia,
            @JsonProperty("temperature_2m_min") String temperaturaMinima,
            @JsonProperty("temperature_2m_max") String temperaturaMaxima,
            @JsonProperty("precipitation_sum") String precipitacao
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespostaHoraria(
            Horario hourly,
            @JsonProperty("hourly_units") UnidadesHorarias hourlyUnits,
            @JsonProperty("utc_offset_seconds") Integer utcOffsetSeconds
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Horario(
            List<String> time,
            @JsonProperty("temperature_2m") List<Double> temperatura,
            @JsonProperty("relative_humidity_2m") List<Double> umidadeAr,
            @JsonProperty("wind_speed_10m") List<Double> vento,
            @JsonProperty("precipitation") List<Double> precipitacao,
            @JsonProperty("soil_moisture_0_to_1cm") List<Double> umidadeSolo
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UnidadesHorarias(
            @JsonProperty("temperature_2m") String temperatura,
            @JsonProperty("relative_humidity_2m") String umidadeAr,
            @JsonProperty("wind_speed_10m") String vento,
            @JsonProperty("precipitation") String precipitacao,
            @JsonProperty("soil_moisture_0_to_1cm") String umidadeSolo
    ) {
    }
}
