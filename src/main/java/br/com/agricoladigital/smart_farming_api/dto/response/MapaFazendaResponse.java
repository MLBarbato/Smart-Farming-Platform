package br.com.agricoladigital.smart_farming_api.dto.response;

import br.com.agricoladigital.smart_farming_api.entity.StatusPlantio;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.List;

/**
 * Dados para desenhar o mapa de uma fazenda: o ponto da fazenda, o polígono de cada talhão e o
 * que a interface mostra ao passar o mouse sobre ele. As coordenadas seguem o GeoJSON
 * ({@code [longitude, latitude]}); a fazenda traz latitude e longitude separadas.
 */
public record MapaFazendaResponse(FazendaMapa fazenda, List<TalhaoMapa> talhoes, ResumoAlertasResponse alertas) {

    public record FazendaMapa(
            Long id,
            String nome,
            String localizacao,
            Double latitude,
            Double longitude,
            String cidade,
            String estado,
            Double areaTotal
    ) {
    }

    public record TalhaoMapa(
            Long id,
            String identificacao,
            Double areaHectares,
            /** GeoJSON Polygon; nulo se o talhão ainda não foi desenhado. */
            JsonNode geometria,
            /** Plantio em cultivo; nulo se o talhão não está com nada plantado agora. */
            PlantioAtual plantioAtual,
            SensoresResumo sensores,
            List<LeituraResponse> leiturasAtuais,
            /** Alertas abertos do talhão; {@code maiorCriticidade} indica a cor do polígono. */
            ResumoAlertasResponse alertas
    ) {
    }

    public record PlantioAtual(
            Long id,
            String culturaNome,
            String tipoPlantio,
            String safra,
            StatusPlantio status,
            LocalDate dataPlantio,
            LocalDate dataColheitaPrevista
    ) {
    }

    public record SensoresResumo(int total, int ativos) {
    }
}
