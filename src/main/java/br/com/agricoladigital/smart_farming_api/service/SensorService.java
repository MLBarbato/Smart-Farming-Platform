package br.com.agricoladigital.smart_farming_api.service;

import br.com.agricoladigital.smart_farming_api.dto.request.AlterarStatusSensorRequest;
import br.com.agricoladigital.smart_farming_api.dto.request.SensorRequest;
import br.com.agricoladigital.smart_farming_api.dto.response.SensorResponse;
import br.com.agricoladigital.smart_farming_api.entity.Dispositivo;
import br.com.agricoladigital.smart_farming_api.entity.Sensor;
import br.com.agricoladigital.smart_farming_api.entity.StatusDispositivo;
import br.com.agricoladigital.smart_farming_api.entity.Talhao;
import br.com.agricoladigital.smart_farming_api.entity.VariavelMonitorada;
import br.com.agricoladigital.smart_farming_api.exception.ConflitoException;
import br.com.agricoladigital.smart_farming_api.exception.RecursoNaoEncontradoException;
import br.com.agricoladigital.smart_farming_api.repository.DispositivoRepository;
import br.com.agricoladigital.smart_farming_api.repository.LeituraRepository;
import br.com.agricoladigital.smart_farming_api.repository.SensorRepository;
import br.com.agricoladigital.smart_farming_api.repository.TalhaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class SensorService {
    static final String TIPO_DISPOSITIVO_SENSOR = "SENSOR";

    private final SensorRepository sensorRepository;
    private final DispositivoRepository dispositivoRepository;
    private final LeituraRepository leituraRepository;
    private final TalhaoRepository talhaoRepository;

    public SensorService(
            SensorRepository sensorRepository,
            DispositivoRepository dispositivoRepository,
            LeituraRepository leituraRepository,
            TalhaoRepository talhaoRepository
    ) {
        this.sensorRepository = sensorRepository;
        this.dispositivoRepository = dispositivoRepository;
        this.leituraRepository = leituraRepository;
        this.talhaoRepository = talhaoRepository;
    }

    @Transactional
    public SensorResponse criar(Long fazendaId, Long talhaoId, SensorRequest request) {
        Talhao talhao = encontrarTalhao(fazendaId, talhaoId);
        String codigo = request.codigo().trim();
        if (dispositivoRepository.existsByCodigoIdentificacao(codigo)) {
            throw new ConflitoException("Já existe um dispositivo com o código: " + codigo);
        }
        Dispositivo dispositivo = dispositivoRepository.save(new Dispositivo(
                codigo, TIPO_DISPOSITIVO_SENSOR, request.dataInstalacao(), StatusDispositivo.ATIVO, talhao
        ));
        Sensor sensor = sensorRepository.save(new Sensor(dispositivo, request.variavel()));
        return paraResponse(sensor);
    }

    public List<SensorResponse> listar(
            Long fazendaId, Long talhaoId, VariavelMonitorada variavel, StatusDispositivo status
    ) {
        encontrarTalhao(fazendaId, talhaoId);
        return sensorRepository.listarPorTalhao(talhaoId).stream()
                .filter(sensor -> variavel == null || sensor.getTipoSensor() == variavel)
                .filter(sensor -> status == null || sensor.getDispositivo().getStatus() == status)
                .map(SensorService::paraResponse)
                .toList();
    }

    public SensorResponse buscar(Long fazendaId, Long talhaoId, Long sensorId) {
        return paraResponse(localizar(fazendaId, talhaoId, sensorId));
    }

    @Transactional
    public SensorResponse alterarStatus(
            Long fazendaId, Long talhaoId, Long sensorId, AlterarStatusSensorRequest request
    ) {
        Sensor sensor = localizar(fazendaId, talhaoId, sensorId);
        sensor.getDispositivo().alterarStatus(request.status());
        return paraResponse(sensor);
    }

    @Transactional
    public void excluir(Long fazendaId, Long talhaoId, Long sensorId) {
        Sensor sensor = localizar(fazendaId, talhaoId, sensorId);
        if (leituraRepository.existsByDispositivo_Id(sensorId)) {
            throw new ConflitoException(
                    "Não é possível excluir um sensor que já tem leituras; desative-o."
            );
        }
        Dispositivo dispositivo = sensor.getDispositivo();
        sensorRepository.delete(sensor);
        dispositivoRepository.delete(dispositivo);
        dispositivoRepository.flush();
    }

    /** Localiza o sensor garantindo que ele pertence ao talhão, e o talhão à fazenda. */
    Sensor localizar(Long fazendaId, Long talhaoId, Long sensorId) {
        encontrarTalhao(fazendaId, talhaoId);
        return sensorRepository.buscarNoTalhao(sensorId, talhaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Sensor não encontrado no talhão informado: " + sensorId
                ));
    }

    Talhao encontrarTalhao(Long fazendaId, Long talhaoId) {
        return talhaoRepository.findByIdAndFazenda_Id(talhaoId, fazendaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Talhão não encontrado na fazenda informada: " + talhaoId
                ));
    }

    static SensorResponse paraResponse(Sensor sensor) {
        Dispositivo dispositivo = sensor.getDispositivo();
        return new SensorResponse(
                sensor.getId(),
                dispositivo.getCodigoIdentificacao(),
                sensor.getTipoSensor(),
                sensor.getUnidadeMedida(),
                dispositivo.getStatus(),
                dispositivo.getDataInstalacao(),
                dispositivo.getTalhao().getId()
        );
    }
}
