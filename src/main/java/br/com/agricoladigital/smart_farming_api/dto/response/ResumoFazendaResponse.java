package br.com.agricoladigital.smart_farming_api.dto.response;

import br.com.agricoladigital.smart_farming_api.entity.SituacaoEstacao;
import br.com.agricoladigital.smart_farming_api.entity.StatusDispositivo;
import br.com.agricoladigital.smart_farming_api.entity.VariavelMonitorada;

import java.time.OffsetDateTime;
import java.util.List;

/** Indicadores de uma fazenda para o dashboard. Todos os instantes são UTC. */
public record ResumoFazendaResponse(
        Fazenda fazenda,
        Talhoes talhoes,
        Sensores sensores,
        Plantios plantios,
        List<CulturaEmCultivo> culturasEmCultivo,
        List<CondicaoAtual> condicoesAtuais,
        List<ProdutividadeSafra> produtividadePorSafra,
        /** Estação meteorológica da fazenda; nula se a fazenda não tem estação. */
        Estacao estacao,
        /** Alertas abertos da fazenda, por criticidade. */
        ResumoAlertasResponse alertas,
        /** Minutos sem leitura a partir dos quais um sensor ativo conta como "sem leitura recente". */
        int criterioSemLeituraRecenteMinutos,
        OffsetDateTime geradoEm
) {
    /** Estação meteorológica com suas últimas leituras; a data é a da última sincronização (UTC). */
    public record Estacao(
            Long id,
            StatusDispositivo status,
            SituacaoEstacao situacao,
            OffsetDateTime ultimaSincronizacao,
            List<LeituraResponse> leiturasAtuais
    ) {
    }

    public record Fazenda(Long id, String nome, String cidade, String estado, Double areaTotal) {
    }

    public record Talhoes(int total, double areaHectares, int comPlantioEmCultivo, double areaEmCultivoHectares) {
    }

    /** Estados dos sensores; {@code semLeituraRecente} conta só sensores {@code ativos}. */
    public record Sensores(int total, int ativos, int inativos, int emManutencao, int semLeituraRecente) {
    }

    public record Plantios(int planejados, int emCultivo, int colhidos, int cancelados) {
    }

    public record CulturaEmCultivo(String cultura, int talhoes, double areaHectares) {
    }

    /**
     * Resumo de uma variável nas leituras recentes dos sensores ativos da fazenda. Leituras mais
     * antigas que o critério de "leitura recente" não entram, para não mostrar dado velho como atual.
     */
    public record CondicaoAtual(
            VariavelMonitorada variavel,
            String unidade,
            double media,
            double minimo,
            double maximo,
            int sensores,
            OffsetDateTime ultimaAtualizacao
    ) {
    }

    /** Produtividade das colheitas da safra: kg colhidos / área dos talhões colhidos. */
    public record ProdutividadeSafra(
            String safra,
            int plantiosColhidos,
            double quantidadeColhidaKg,
            double areaHectares,
            double produtividadeKgPorHectare
    ) {
    }
}
