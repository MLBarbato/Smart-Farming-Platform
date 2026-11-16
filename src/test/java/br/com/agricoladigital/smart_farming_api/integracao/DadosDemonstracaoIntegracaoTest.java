package br.com.agricoladigital.smart_farming_api.integracao;

import br.com.agricoladigital.smart_farming_api.entity.*;
import br.com.agricoladigital.smart_farming_api.repository.RecomendacaoIrrigacaoRepository;
import br.com.agricoladigital.smart_farming_api.service.DadosDemonstracao;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O carregador de dados de demonstração sobre PostgreSQL real, sem acesso ao Open-Meteo (a URL do provedor
 * aponta para uma porta fechada): os dados locais devem ficar completos e a falta de previsão deve aparecer
 * como "dados insuficientes", sem derrubar nada.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.demo.enabled=true",
        "app.demo.password=senha-demo-123",
        "app.previsao.url=http://localhost:1/previsao-inexistente"
})
class DadosDemonstracaoIntegracaoTest extends IntegracaoPostgresBase {
    @Autowired private DadosDemonstracao demonstracao;
    @Autowired private MockMvc mvc;
    @Autowired private RecomendacaoIrrigacaoRepository recomendacaoRepository;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private <T> T naTransacao(java.util.function.Supplier<T> acao) {
        return new org.springframework.transaction.support.TransactionTemplate(transactionManager).execute(status -> acao.get());
    }

    private String login(String email) throws Exception {
        String corpo = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"senha-demo-123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(corpo, "$.accessToken");
    }

    @Test
    void carregaUmConjuntoCompletoECoerenteDeDados() throws Exception {
        DadosDemonstracao.Resumo resumo = demonstracao.carregar();

        assertTrue(resumo.carregado());
        assertEquals(3, resumo.fazendas());
        assertEquals(9, resumo.talhoes());
        assertEquals(15, resumo.sensores());
        assertEquals(15, jdbc.queryForObject("select count(*) from sensor where id_estacao is null", Integer.class));
        // as estações virtuais são criadas mesmo sem o provedor (ficam SEM_SINCRONIZACAO, sem leituras)
        assertEquals(3, jdbc.queryForObject("select count(*) from estacao_meteorologica where ultima_sincronizacao is null", Integer.class));
        assertFalse(resumo.previsaoObtida(), "sem acesso ao provedor");
        assertTrue(resumo.leituras() > 15 * 280, "72 h a cada 15 min por sensor: " + resumo.leituras());

        // culturas com parâmetros, talhões com polígono e plantios nos três estados
        assertEquals(3, culturaRepository.count());
        assertTrue(culturaRepository.findAll().stream().allMatch(Cultura::temParametrosDeIrrigacao));
        assertEquals(9, jdbc.queryForObject("select count(*) from talhao where geometria is not null", Integer.class));
        Map<StatusPlantio, Long> plantios = plantioRepository.findAll().stream()
                .collect(Collectors.groupingBy(Plantio::getStatus, Collectors.counting()));
        assertEquals(Map.of(StatusPlantio.EM_CULTIVO, 5L, StatusPlantio.COLHIDO, 3L, StatusPlantio.PLANEJADO, 1L), plantios);
    }

    @Test
    void geraAlertasDeSecaGraveELeveEDeFalhaDeSensorEDeixaUmEmAtendimento() {
        demonstracao.carregar();

        Map<String, Alerta> porChave = naTransacao(() -> alertaRepository.findAll().stream().collect(Collectors.toMap(
                a -> a.getTalhao().getFazenda().getNome() + "/" + a.getTalhao().getIdentificacao() + "/" + a.getEvento(),
                a -> a)));
        assertEquals(CriticidadeAlerta.ALTA, porChave.get("Fazenda Santa Maria/Talhão 02/SECA").getCriticidade());
        assertEquals(CriticidadeAlerta.BAIXA, porChave.get("Fazenda Boa Esperança/Talhão 01/SECA").getCriticidade());
        assertEquals(CriticidadeAlerta.MEDIA, porChave.get("Fazenda Boa Esperança/Talhão 02/FALHA_SENSOR").getCriticidade());
        assertEquals(3, porChave.size(), "nada além do esperado: " + porChave.keySet());
        assertEquals(StatusAlerta.EM_ATENDIMENTO, porChave.get("Fazenda Santa Maria/Talhão 02/SECA").getStatus());
        assertEquals(StatusAlerta.ABERTO, porChave.get("Fazenda Boa Esperança/Talhão 01/SECA").getStatus());
    }

    @Test
    void semPrevisaoOSoloSecoNaoGeraSugestaoDeIrrigacao() {
        demonstracao.carregar();

        Map<String, SituacaoIrrigacao> porTalhao = naTransacao(() -> recomendacaoRepository.findAll().stream().collect(Collectors.toMap(
                r -> r.getTalhao().getFazenda().getNome() + "/" + r.getTalhao().getIdentificacao(),
                RecomendacaoIrrigacao::getSituacao)));
        assertEquals(SituacaoIrrigacao.NAO_IRRIGAR, porTalhao.get("Fazenda Santa Maria/Talhão 01"));
        assertEquals(SituacaoIrrigacao.SEM_DADOS_SUFICIENTES, porTalhao.get("Fazenda Santa Maria/Talhão 02"));
        assertEquals(SituacaoIrrigacao.SEM_DADOS_SUFICIENTES, porTalhao.get("Fazenda Boa Esperança/Talhão 01"));
        assertEquals(SituacaoIrrigacao.SEM_DADOS_SUFICIENTES, porTalhao.get("Fazenda Boa Esperança/Talhão 02"),
                "sensor parado: faltam leituras de umidade");
        assertEquals(SituacaoIrrigacao.NAO_IRRIGAR, porTalhao.get("Sítio Água Limpa/Talhão 01"));
    }

    @Test
    void osUsuariosDemoEntramEVeemSoOQueELesPertence() throws Exception {
        demonstracao.carregar();
        String ana = login(DadosDemonstracao.EMAIL_ANA);
        String bruno = login(DadosDemonstracao.EMAIL_BRUNO);

        mvc.perform(get("/api/v1/resumo").header("Authorization", "Bearer " + ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totais.fazendas").value(2))
                .andExpect(jsonPath("$.totais.talhoes").value(7))
                .andExpect(jsonPath("$.totais.alertas.abertos").value(3));
        mvc.perform(get("/api/v1/resumo").header("Authorization", "Bearer " + bruno))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totais.fazendas").value(1))
                .andExpect(jsonPath("$.totais.alertas.abertos").value(0));

        Long fazendaDaAna = fazendaRepository.findAll().stream()
                .filter(f -> f.getNome().equals("Fazenda Santa Maria")).findFirst().orElseThrow().getId();
        mvc.perform(get("/api/v1/fazendas/" + fazendaDaAna + "/mapa").header("Authorization", "Bearer " + ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.talhoes.length()").value(4))
                .andExpect(jsonPath("$.talhoes[1].alertas.maiorCriticidade").value("ALTA"))
                .andExpect(jsonPath("$.talhoes[0].geometria.type").value("Polygon"));
        mvc.perform(get("/api/v1/fazendas/" + fazendaDaAna + "/mapa").header("Authorization", "Bearer " + bruno))
                .andExpect(status().isForbidden());
        // safra colhida com produtividade calculada
        mvc.perform(get("/api/v1/fazendas/" + fazendaDaAna + "/resumo").header("Authorization", "Bearer " + ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.produtividadePorSafra.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void carregarDeNovoNaoDuplicaNada() {
        assertTrue(demonstracao.carregar().carregado());
        int sensores = jdbc.queryForObject("select count(*) from sensor", Integer.class);
        long alertas = alertaRepository.count();

        assertFalse(demonstracao.carregar().carregado());

        assertEquals(sensores, jdbc.queryForObject("select count(*) from sensor", Integer.class));
        assertEquals(alertas, alertaRepository.count());
        assertEquals(2, usuarioRepository.count());
    }
}
