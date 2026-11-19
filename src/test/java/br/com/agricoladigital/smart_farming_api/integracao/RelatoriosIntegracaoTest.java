package br.com.agricoladigital.smart_farming_api.integracao;

import br.com.agricoladigital.smart_farming_api.entity.*;
import br.com.agricoladigital.smart_farming_api.repository.RecomendacaoIrrigacaoRepository;
import br.com.agricoladigital.smart_farming_api.repository.ResumoLeiturasSensor;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Consultas dos relatórios em PostgreSQL real e a rota de exportação em CSV. */
@AutoConfigureMockMvc
class RelatoriosIntegracaoTest extends IntegracaoPostgresBase {
    private static final String SENHA = "senha-segura-123";

    @Autowired private MockMvc mvc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private RecomendacaoIrrigacaoRepository recomendacaoRepository;

    private static LocalDateTime utc(String iso) {
        return LocalDateTime.parse(iso);
    }

    private Usuario usuarioComSenha(String email) {
        return usuarioRepository.save(new Usuario("Ana", email, passwordEncoder.encode(SENHA), PerfilUsuario.AGRICULTOR));
    }

    private String login(String email) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"senha\":\"" + SENHA + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return JsonPath.read(r.getResponse().getContentAsString(), "$.accessToken");
    }

    // ---- consultas ----

    @Test
    void consultasDoPeriodoRespeitamAFazendaEOIntervaloSemiaberto() {
        Fazenda fazenda = novaFazenda(novoUsuario(PerfilUsuario.AGRICULTOR));
        Fazenda outra = novaFazenda(fazenda.getUsuario());
        Talhao t1 = novoTalhao(fazenda, "T1");
        Talhao deOutra = novoTalhao(outra, "X1");
        LocalDateTime de = utc("2026-11-01T03:00:00");
        LocalDateTime ate = utc("2026-11-08T03:00:00");

        String sql = "insert into recomendacao_irrigacao (id_talhao, data_hora, valido_ate, situacao) values (?, ?, ?, 'NAO_IRRIGAR')";
        jdbc.update(sql, t1.getId(), de, de.plusHours(6));                      // no início: entra
        jdbc.update(sql, t1.getId(), ate, ate.plusHours(6));                    // no fim (exclusivo): fica de fora
        jdbc.update(sql, deOutra.getId(), de.plusDays(1), de.plusDays(1).plusHours(6));   // outra fazenda
        assertEquals(List.of(de), recomendacaoRepository.buscarNoPeriodo(fazenda.getId(), de, ate).stream()
                .map(RecomendacaoIrrigacao::getDataHora).toList());

        Usuario atendente = novoUsuario(PerfilUsuario.AGRICULTOR);
        Alerta assumido = new Alerta(regra(EventoAlerta.SECA, CriticidadeAlerta.ALTA), t1, null, de.plusHours(1), "a");
        assumido.assumir(atendente, de.plusHours(2), null);
        alertaRepository.saveAndFlush(assumido);
        alertaRepository.saveAndFlush(new Alerta(regra(EventoAlerta.RISCO_GEADA, CriticidadeAlerta.BAIXA), t1, null, de.minusMinutes(1), "antes"));
        alertaRepository.saveAndFlush(new Alerta(regra(EventoAlerta.SECA, CriticidadeAlerta.ALTA), deOutra, null, de.plusHours(1), "outra"));
        List<Alerta> alertas = alertaRepository.buscarNoPeriodo(fazenda.getId(), de, ate);
        assertEquals(1, alertas.size());
        assertEquals(atendente.getNome(), alertas.get(0).getAtendente().getNome(), "atendente carregado junto");
    }

    @Test
    void plantiosDoPeriodoSaoOsQueOcuparamAlgumDiaDele() {
        Fazenda fazenda = novaFazenda(novoUsuario(PerfilUsuario.AGRICULTOR));
        Talhao talhao = novoTalhao(fazenda, "T1");
        Cultura soja = novaCultura("Soja", 30.0, 38.0, 30);
        Plantio colhidoAntes = new Plantio(talhao, soja, "Direto", "2024/2025", LocalDate.of(2024, 10, 1), null);
        colhidoAntes.colher(LocalDate.of(2025, 2, 1), 1000.0);
        Plantio colhidoDentro = new Plantio(talhao, soja, "Direto", "2025/2026", LocalDate.of(2025, 10, 1), null);
        colhidoDentro.colher(LocalDate.of(2026, 2, 10), 14000.0);
        Plantio emCultivo = new Plantio(talhao, soja, "Direto", "2026/2027", LocalDate.of(2026, 3, 1), null);
        Plantio futuro = new Plantio(talhao, soja, "Direto", "2027/2028", LocalDate.of(2027, 10, 1), null);
        plantioRepository.saveAll(List.of(colhidoAntes, colhidoDentro, emCultivo, futuro));

        List<String> safras = plantioRepository.buscarNoPeriodo(fazenda.getId(), LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 3, 31)).stream().map(Plantio::getSafra).toList();

        assertEquals(List.of("2025/2026", "2026/2027"), safras);
    }

    @Test
    void leiturasDoPeriodoEResumoPorSensor() {
        Talhao talhao = novoTalhao(novaFazenda(novoUsuario(PerfilUsuario.AGRICULTOR)), "T1");
        Sensor sensor = novoSensor(talhao, VariavelMonitorada.UMIDADE_SOLO);
        Sensor semLeituraNoPeriodo = novoSensor(talhao, VariavelMonitorada.UMIDADE_SOLO);
        LocalDateTime de = utc("2026-11-01T03:00:00");
        LocalDateTime ate = utc("2026-11-02T03:00:00");
        leitura(sensor, 40.0, de);
        leitura(sensor, 50.0, de.plusHours(5));
        leitura(sensor, 99.0, ate);                       // fim exclusivo
        leitura(semLeituraNoPeriodo, 10.0, de.minusHours(1));
        List<Long> ids = List.of(sensor.getId(), semLeituraNoPeriodo.getId());

        assertEquals(List.of(40.0, 50.0), leituraRepository.buscarNoPeriodo(ids, de, ate).stream()
                .map(Leitura::getValor).toList());
        List<ResumoLeiturasSensor> resumos = leituraRepository.resumirNoPeriodo(ids, de, ate);
        assertEquals(List.of(new ResumoLeiturasSensor(sensor.getId(), 2, de, de.plusHours(5), 40.0, 45.0, 50.0)), resumos);
    }

    // ---- rota ----

    @Test
    void exportaCsvComoAnexoEBloqueiaFazendaDeOutraConta() throws Exception {
        Usuario ana = usuarioComSenha("ana.relatorio@example.com");
        Fazenda daAna = novaFazenda(ana);
        Talhao talhao = novoTalhao(daAna, "Talhão Norte");
        Cultura soja = novaCultura("Soja", 30.0, 38.0, 30);
        plantioEmCultivo(talhao, soja);
        Fazenda deOutro = novaFazenda(novoUsuario(PerfilUsuario.AGRICULTOR));
        String token = login(ana.getEmail());

        MvcResult resultado = mvc.perform(get("/api/v1/fazendas/{id}/relatorios/PRODUTIVIDADE", daAna.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition",
                        containsString("relatorio-produtividade-fazenda-" + daAna.getId())))
                .andReturn();
        String csv = new String(resultado.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("﻿Talhão;Área do talhão (ha);Cultura"), csv);
        assertTrue(csv.contains("Talhão Norte;10;" + soja.getNome() + ";2026/2027;Direto;Em cultivo;"), csv);

        mvc.perform(get("/api/v1/fazendas/{id}/relatorios/ALERTAS", deOutro.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/fazendas/{id}/relatorios/ALERTAS", daAna.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void errosDoRelatorioVoltamNoFormatoPadrao() throws Exception {
        Usuario ana = usuarioComSenha("ana.erros@example.com");
        Fazenda fazenda = novaFazenda(ana);
        String token = login(ana.getEmail());

        mvc.perform(get("/api/v1/fazendas/{id}/relatorios/ALERTAS", fazenda.getId())
                        .param("de", "2026-11-10").param("ate", "2026-11-01")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value("O início do período não pode ser posterior ao fim."));
        mvc.perform(get("/api/v1/fazendas/{id}/relatorios/CLIMA", fazenda.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem", containsString("estação virtual")));
        mvc.perform(get("/api/v1/fazendas/{id}/relatorios/INEXISTENTE", fazenda.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }
}
