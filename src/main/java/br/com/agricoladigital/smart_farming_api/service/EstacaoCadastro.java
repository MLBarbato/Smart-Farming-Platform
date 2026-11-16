package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.entity.Dispositivo;
import br.com.agricoladigital.smart_farming_api.entity.EstacaoMeteorologica;
import br.com.agricoladigital.smart_farming_api.entity.Fazenda;
import br.com.agricoladigital.smart_farming_api.entity.FonteEstacao;
import br.com.agricoladigital.smart_farming_api.entity.Sensor;
import br.com.agricoladigital.smart_farming_api.entity.StatusDispositivo;
import br.com.agricoladigital.smart_farming_api.entity.VariavelMonitorada;
import br.com.agricoladigital.smart_farming_api.exception.ConflitoException;
import br.com.agricoladigital.smart_farming_api.exception.RecursoNaoEncontradoException;
import br.com.agricoladigital.smart_farming_api.exception.RegraDeNegocioException;
import br.com.agricoladigital.smart_farming_api.repository.DispositivoRepository;
import br.com.agricoladigital.smart_farming_api.repository.EstacaoMeteorologicaRepository;
import br.com.agricoladigital.smart_farming_api.repository.FazendaRepository;
import br.com.agricoladigital.smart_farming_api.repository.LeituraRepository;
import br.com.agricoladigital.smart_farming_api.repository.SensorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/** Operações transacionais sobre a estação virtual da fazenda (nunca fazem chamadas externas). */
@Component
public class EstacaoCadastro {
    static final String TIPO_ESTACAO = "ESTACAO";
    static final String MODELO_VIRTUAL = "Open-Meteo (estação virtual)";
    /** O provedor é consultado de hora em hora. */
    static final int FREQUENCIA_COLETA_MINUTOS = 60;
    /** Variáveis que o Open-Meteo fornece; pH e luminosidade não estão disponíveis. */
    static final List<VariavelMonitorada> VARIAVEIS = List.of(
            VariavelMonitorada.TEMPERATURA_AR,
            VariavelMonitorada.UMIDADE_AR,
            VariavelMonitorada.VELOCIDADE_VENTO,
            VariavelMonitorada.PRECIPITACAO,
            VariavelMonitorada.UMIDADE_SOLO
    );

    private final FazendaRepository fazendaRepository;
    private final EstacaoMeteorologicaRepository estacaoRepository;
    private final DispositivoRepository dispositivoRepository;
    private final SensorRepository sensorRepository;
    private final LeituraRepository leituraRepository;
    private final Clock clock;

    @Autowired
    public EstacaoCadastro(
            FazendaRepository fazendaRepository,
            EstacaoMeteorologicaRepository estacaoRepository,
            DispositivoRepository dispositivoRepository,
            SensorRepository sensorRepository,
            LeituraRepository leituraRepository
    ) {
        this(fazendaRepository, estacaoRepository, dispositivoRepository, sensorRepository, leituraRepository,
                Clock.systemUTC());
    }

    EstacaoCadastro(
            FazendaRepository fazendaRepository,
            EstacaoMeteorologicaRepository estacaoRepository,
            DispositivoRepository dispositivoRepository,
            SensorRepository sensorRepository,
            LeituraRepository leituraRepository,
            Clock clock
    ) {
        this.fazendaRepository = fazendaRepository;
        this.estacaoRepository = estacaoRepository;
        this.dispositivoRepository = dispositivoRepository;
        this.sensorRepository = sensorRepository;
        this.leituraRepository = leituraRepository;
        this.clock = clock;
    }

    /** Cria a estação virtual da fazenda com um sensor por variável fornecida pelo provedor. */
    @Transactional
    public void criar(Long fazendaId) {
        Fazenda fazenda = fazendaRepository.findById(fazendaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Fazenda não encontrada: " + fazendaId));
        if (fazenda.getLatitude() == null || fazenda.getLongitude() == null) {
            throw new RegraDeNegocioException(
                    "Cadastre a latitude e a longitude da fazenda para criar a estação virtual."
            );
        }
        if (estacaoRepository.buscarDaFazenda(fazendaId).isPresent()) {
            throw new ConflitoException("A fazenda já tem uma estação meteorológica.");
        }
        String codigo = codigoDaEstacao(fazendaId);
        if (dispositivoRepository.existsByCodigoIdentificacao(codigo)) {
            throw new ConflitoException("Já existe um dispositivo com o código: " + codigo);
        }
        LocalDate hoje = LocalDate.now(clock);
        Dispositivo dispositivo = dispositivoRepository.save(
                new Dispositivo(codigo, TIPO_ESTACAO, hoje, StatusDispositivo.ATIVO, fazenda));
        EstacaoMeteorologica estacao = estacaoRepository.save(new EstacaoMeteorologica(
                dispositivo, MODELO_VIRTUAL, FREQUENCIA_COLETA_MINUTOS, FonteEstacao.OPEN_METEO));
        for (VariavelMonitorada variavel : VARIAVEIS) {
            String codigoSensor = codigo + "-" + variavel;
            if (dispositivoRepository.existsByCodigoIdentificacao(codigoSensor)) {
                throw new ConflitoException("Já existe um dispositivo com o código: " + codigoSensor);
            }
            Dispositivo dispositivoSensor = dispositivoRepository.save(
                    new Dispositivo(codigoSensor, SensorService.TIPO_DISPOSITIVO_SENSOR, hoje,
                            StatusDispositivo.ATIVO, fazenda));
            sensorRepository.save(new Sensor(dispositivoSensor, variavel, estacao));
        }
    }

    @Transactional
    public void alterarStatus(Long fazendaId, StatusDispositivo status) {
        encontrar(fazendaId).getDispositivo().alterarStatus(status);
    }

    @Transactional
    public void registrarSincronizacao(Long estacaoId, LocalDateTime instanteUtc) {
        estacaoRepository.findById(estacaoId).ifPresent(estacao -> estacao.registrarSincronizacao(instanteUtc));
    }

    /**
     * Remove a estação da fazenda, se houver, junto com seus sensores e leituras. Como os dados
     * vêm do provedor externo, podem ser obtidos de novo recriando a estação.
     */
    @Transactional
    public void removerSeExistir(Long fazendaId) {
        estacaoRepository.buscarDaFazenda(fazendaId).ifPresent(estacao -> {
            List<Sensor> sensores = sensorRepository.listarDaEstacao(estacao.getId());
            List<Dispositivo> dispositivos = new ArrayList<>(sensores.stream().map(Sensor::getDispositivo).toList());
            dispositivos.add(estacao.getDispositivo());
            leituraRepository.deleteAllByDispositivo_IdIn(dispositivos.stream().map(Dispositivo::getId).toList());
            sensorRepository.deleteAll(sensores);
            estacaoRepository.delete(estacao);
            dispositivoRepository.deleteAll(dispositivos);
            dispositivoRepository.flush();
        });
    }

    /** Remove a estação ou falha com 404 se a fazenda não a tem. */
    @Transactional
    public void remover(Long fazendaId) {
        encontrar(fazendaId);
        removerSeExistir(fazendaId);
    }

    EstacaoMeteorologica encontrar(Long fazendaId) {
        return estacaoRepository.buscarDaFazenda(fazendaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "A fazenda não tem estação meteorológica: " + fazendaId));
    }

    static String codigoDaEstacao(Long fazendaId) {
        return "EST-" + fazendaId;
    }
}
