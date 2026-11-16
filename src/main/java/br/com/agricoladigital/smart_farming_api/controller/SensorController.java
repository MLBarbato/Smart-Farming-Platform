package br.com.agricoladigital.smart_farming_api.controller;

import io.swagger.v3.oas.annotations.Operation;
import br.com.agricoladigital.smart_farming_api.config.Erro;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import br.com.agricoladigital.smart_farming_api.dto.request.AlterarStatusSensorRequest;
import br.com.agricoladigital.smart_farming_api.dto.request.SensorRequest;
import br.com.agricoladigital.smart_farming_api.dto.response.SensorResponse;
import br.com.agricoladigital.smart_farming_api.entity.StatusDispositivo;
import br.com.agricoladigital.smart_farming_api.entity.VariavelMonitorada;
import br.com.agricoladigital.smart_farming_api.service.SensorService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@Tag(name = "Sensores", description = "Sensores de um talhão (variável, estado e instalação).")
@RestController
@RequestMapping("/api/v1/fazendas/{fazendaId}/talhoes/{talhaoId}/sensores")
@PreAuthorize("@acesso.podeAcessarFazenda(authentication, #fazendaId)")
public class SensorController {
    private final SensorService sensorService;

    public SensorController(SensorService sensorService) {
        this.sensorService = sensorService;
    }

    @Operation(summary = "Criar sensor", description = "O código é único; nasce ATIVO. A unidade vem fixa da variável.")
    @Erro(codigo = "409", descricao = "Código já cadastrado")
    @PostMapping
    public ResponseEntity<SensorResponse> criar(
            @PathVariable Long fazendaId,
            @PathVariable Long talhaoId,
            @Valid @RequestBody SensorRequest request
    ) {
        SensorResponse response = sensorService.criar(fazendaId, talhaoId, request);
        return ResponseEntity.created(URI.create(
                "/api/v1/fazendas/" + fazendaId + "/talhoes/" + talhaoId + "/sensores/" + response.id()
        )).body(response);
    }

    @Operation(summary = "Listar sensores do talhão", description = "Filtros opcionais: variavel e status.")
    @GetMapping
    public List<SensorResponse> listar(
            @PathVariable Long fazendaId,
            @PathVariable Long talhaoId,
            @RequestParam(required = false) VariavelMonitorada variavel,
            @RequestParam(required = false) StatusDispositivo status
    ) {
        return sensorService.listar(fazendaId, talhaoId, variavel, status);
    }

    @Operation(summary = "Consultar sensor")
    @GetMapping("/{sensorId}")
    public SensorResponse buscar(
            @PathVariable Long fazendaId,
            @PathVariable Long talhaoId,
            @PathVariable Long sensorId
    ) {
        return sensorService.buscar(fazendaId, talhaoId, sensorId);
    }

    @Operation(summary = "Mudar o estado do sensor", description = "ATIVO, INATIVO ou MANUTENCAO. Só sensores ATIVO aceitam leituras.")
    @PatchMapping("/{sensorId}/status")
    public SensorResponse alterarStatus(
            @PathVariable Long fazendaId,
            @PathVariable Long talhaoId,
            @PathVariable Long sensorId,
            @Valid @RequestBody AlterarStatusSensorRequest request
    ) {
        return sensorService.alterarStatus(fazendaId, talhaoId, sensorId, request);
    }

    @Operation(summary = "Excluir sensor")
    @Erro(codigo = "409", descricao = "Sensor que já tem leituras (desative-o em vez de excluir)")
    @DeleteMapping("/{sensorId}")
    public ResponseEntity<Void> excluir(
            @PathVariable Long fazendaId,
            @PathVariable Long talhaoId,
            @PathVariable Long sensorId
    ) {
        sensorService.excluir(fazendaId, talhaoId, sensorId);
        return ResponseEntity.noContent().build();
    }
}
