package br.com.agricoladigital.smart_farming_api.integracao;

import br.com.agricoladigital.smart_farming_api.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Migrações Flyway e garantias do banco (constraints, índices únicos e chaves compostas) em PostgreSQL real. */
class MigracoesIntegracaoTest extends IntegracaoPostgresBase {

    @Test
    void todasAsMigracoesForamAplicadasComSucessoEOMapeamentoJpaConfere() {
        Integer falhas = jdbc.queryForObject("select count(*) from flyway_schema_history where success = false", Integer.class);
        Integer ultima = jdbc.queryForObject(
                "select max(version::int) from flyway_schema_history where success", Integer.class);

        assertEquals(0, falhas);
        assertTrue(ultima >= 28, "última migração aplicada: " + ultima);
        // ddl-auto=validate: se algum mapeamento divergisse do esquema, o contexto nem subiria.
    }

    @Test
    void regrasDeAlertaVemSemeadasPorEventoECriticidade() {
        assertEquals(11, regraRepository.count());
        assertEquals(3, regraRepository.findAllByEvento(EventoAlerta.SECA).size());
        assertEquals(2, regraRepository.findAllByEvento(EventoAlerta.FALHA_SENSOR).size());
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into regra_alerta (evento, criticidade, operador, limiar, unidade, ativa) "
                        + "values ('SECA', 'BAIXA', 'MENOR_QUE', 10, '%', true)"));
    }

    // ---- alertas ----

    private Alerta novoAlerta(Talhao talhao, EventoAlerta evento, CriticidadeAlerta criticidade) {
        return alertaRepository.saveAndFlush(new Alerta(regra(evento, criticidade), talhao, null, agoraUtc(), "msg"));
    }

    @Test
    void soUmAlertaAbertoPorTalhaoEEventoMasOutrosCombinacoesEFechadosPassam() {
        Fazenda fazenda = novaFazenda(novoUsuario(PerfilUsuario.AGRICULTOR));
        Talhao t1 = novoTalhao(fazenda, "T1");
        Talhao t2 = novoTalhao(fazenda, "T2");
        Alerta primeiro = novoAlerta(t1, EventoAlerta.SECA, CriticidadeAlerta.BAIXA);

        assertThrows(DataIntegrityViolationException.class, () -> novoAlerta(t1, EventoAlerta.SECA, CriticidadeAlerta.ALTA));
        novoAlerta(t2, EventoAlerta.SECA, CriticidadeAlerta.BAIXA);              // outro talhão
        novoAlerta(t1, EventoAlerta.RISCO_GEADA, CriticidadeAlerta.BAIXA);       // outro evento

        primeiro.resolverAutomaticamente(agoraUtc());
        alertaRepository.saveAndFlush(primeiro);
        novoAlerta(t1, EventoAlerta.SECA, CriticidadeAlerta.MEDIA);              // o anterior fechou
        assertEquals(1, alertaRepository.buscarFechadosDesde(fazenda.getId(),
                List.of(StatusAlerta.RESOLVIDO), agoraUtc().minusHours(1)).size());
    }

    @Test
    void alertaFechadoExigeDataDeFechamentoEValoresInvalidosSaoRecusados() {
        Talhao talhao = novoTalhao(novaFazenda(novoUsuario(PerfilUsuario.AGRICULTOR)), "T1");
        Alerta alerta = novoAlerta(talhao, EventoAlerta.SECA, CriticidadeAlerta.BAIXA);
        Long id = alerta.getId();

        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("update alerta set status = 'RESOLVIDO' where id_alerta = ?", id));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("update alerta set resolvido_em = now() where id_alerta = ?", id));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("update alerta set status = 'TALVEZ' where id_alerta = ?", id));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("update alerta set criticidade = 'URGENTE' where id_alerta = ?", id));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "update alerta set status = 'DISPENSADO', resolvido_em = now(), resolucao = 'MANUAL' where id_alerta = ?", id));
    }

    @Test
    void leituraQueOriginouUmAlertaNaoPodeSerApagada() {
        Talhao talhao = novoTalhao(novaFazenda(novoUsuario(PerfilUsuario.AGRICULTOR)), "T1");
        Sensor sensor = novoSensor(talhao, VariavelMonitorada.UMIDADE_SOLO);
        Leitura leitura = leitura(sensor, 10.0, agoraUtc());
        alertaRepository.saveAndFlush(new Alerta(regra(EventoAlerta.SECA, CriticidadeAlerta.ALTA), talhao, leitura,
                agoraUtc(), "msg"));

        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("delete from leitura where id_leitura = ?", leitura.getId()));
    }

    // ---- dispositivos, sensores e leituras ----

    @Test
    void leituraNaoRepeteOMesmoInstanteNoMesmoDispositivo() {
        Talhao talhao = novoTalhao(novaFazenda(novoUsuario(PerfilUsuario.AGRICULTOR)), "T1");
        Sensor sensor = novoSensor(talhao, VariavelMonitorada.TEMPERATURA_AR);
        LocalDateTime instante = agoraUtc();
        leitura(sensor, 20.0, instante);

        assertThrows(DataIntegrityViolationException.class, () -> leituraRepository.saveAndFlush(
                new Leitura(sensor.getDispositivo(), 21.0, instante, OrigemLeitura.SIMULADA)));
    }

    @Test
    void todasAsVariaveisMonitoradasSaoAceitasPeloBancoEOutrasNao() {
        Talhao talhao = novoTalhao(novaFazenda(novoUsuario(PerfilUsuario.AGRICULTOR)), "T1");
        for (VariavelMonitorada variavel : VariavelMonitorada.values()) {
            assertNotNull(novoSensor(talhao, variavel).getId(), "variável sem suporte no banco: " + variavel);
        }
        assertTrue(VariavelMonitorada.valueOf("TEMPERATURA_SOLO").aceita(24.0));
        assertTrue(VariavelMonitorada.valueOf("CONDUTIVIDADE_ELETRICA").aceita(1.8));
        Long id = novoSensor(talhao, VariavelMonitorada.UMIDADE_AR).getId();
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("update sensor set tipo_sensor = 'RADIACAO' where id_dispositivo = ?", id));
    }

    @Test
    void dispositivoNaoPodeApontarParaTalhaoDeOutraFazenda() {
        Usuario dono = novoUsuario(PerfilUsuario.AGRICULTOR);
        Fazenda a = novaFazenda(dono);
        Fazenda b = novaFazenda(dono);
        Talhao talhaoDeB = novoTalhao(b, "TB");

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into dispositivo (codigo_identificacao, tipo_dispositivo, status, id_fazenda, id_talhao) "
                        + "values ('X-1', 'SENSOR', 'ATIVO', ?, ?)", a.getId(), talhaoDeB.getId()));
    }

    @Test
    void soExisteUmaEstacaoPorFazendaMasOutraFazendaPodeTerAsua() {
        Usuario dono = novoUsuario(PerfilUsuario.AGRICULTOR);
        Fazenda fazenda = novaFazenda(dono);
        Dispositivo primeira = dispositivoRepository.saveAndFlush(
                new Dispositivo("EST-1", "ESTACAO", null, StatusDispositivo.ATIVO, fazenda));
        jdbc.update("insert into estacao_meteorologica (id_dispositivo, fonte) values (?, 'OPEN_METEO')", primeira.getId());

        assertThrows(DataIntegrityViolationException.class, () -> dispositivoRepository.saveAndFlush(
                new Dispositivo("EST-2", "ESTACAO", null, StatusDispositivo.ATIVO, fazenda)));
        assertNotNull(dispositivoRepository.saveAndFlush(
                new Dispositivo("EST-3", "ESTACAO", null, StatusDispositivo.ATIVO, novaFazenda(dono))).getId());
    }

    // ---- culturas e recomendações ----

    @Test
    void parametrosDeIrrigacaoDaCulturaSaoValidadosNoBanco() {
        Cultura cultura = culturaRepository.saveAndFlush(new Cultura("Soja", 120));
        Long id = cultura.getId();

        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("update cultura set umidade_minima = 30 where id_cultura = ?", id));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "update cultura set umidade_minima = 40, umidade_ideal = 40, profundidade_raiz_cm = 30 where id_cultura = ?", id));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "update cultura set umidade_minima = 30, umidade_ideal = 120, profundidade_raiz_cm = 30 where id_cultura = ?", id));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "update cultura set umidade_minima = 30, umidade_ideal = 40, profundidade_raiz_cm = 500 where id_cultura = ?", id));
        assertEquals(1, jdbc.update(
                "update cultura set umidade_minima = 30, umidade_ideal = 40, profundidade_raiz_cm = 30 where id_cultura = ?", id));
    }

    @Test
    void recomendacaoSoTemVolumeQuandoOResultadoEIrrigar() {
        Talhao talhao = novoTalhao(novaFazenda(novoUsuario(PerfilUsuario.AGRICULTOR)), "T1");
        String base = "insert into recomendacao_irrigacao (id_talhao, data_hora, valido_ate, situacao, volume_recomendado_mm) "
                + "values (?, now(), now(), ?, ?)";

        assertEquals(1, jdbc.update(base, talhao.getId(), "IRRIGAR", 20.0));
        assertEquals(1, jdbc.update(base, talhao.getId(), "NAO_IRRIGAR", null));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(base, talhao.getId(), "IRRIGAR", null));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(base, talhao.getId(), "IRRIGAR", 0.0));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(base, talhao.getId(), "AGUARDAR_CHUVA", 5.0));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(base, talhao.getId(), "TALVEZ", null));
    }

    @Test
    void substituirAPrevisaoNaoApagaARecomendacaoQueAReferencia() {
        Fazenda fazenda = novaFazenda(novoUsuario(PerfilUsuario.AGRICULTOR));
        Talhao talhao = novoTalhao(fazenda, "T1");
        jdbc.update("insert into previsao_tempo (id_fazenda, data_previsao, consultada_em) values (?, current_date, now())",
                fazenda.getId());
        Long idPrevisao = jdbc.queryForObject("select id_previsao from previsao_tempo", Long.class);
        jdbc.update("insert into recomendacao_irrigacao (id_talhao, id_previsao, data_hora, valido_ate, situacao) "
                + "values (?, ?, now(), now(), 'NAO_IRRIGAR')", talhao.getId(), idPrevisao);

        jdbc.update("delete from previsao_tempo");

        assertEquals(1, jdbc.queryForObject("select count(*) from recomendacao_irrigacao where id_previsao is null", Integer.class));
    }

    @Test
    void talhaoComAlertaOuRecomendacaoNaoPodeSerApagado() {
        Talhao talhao = novoTalhao(novaFazenda(novoUsuario(PerfilUsuario.AGRICULTOR)), "T1");
        novoAlerta(talhao, EventoAlerta.SECA, CriticidadeAlerta.BAIXA);

        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("delete from talhao where id_talhao = ?", talhao.getId()));
    }
}
