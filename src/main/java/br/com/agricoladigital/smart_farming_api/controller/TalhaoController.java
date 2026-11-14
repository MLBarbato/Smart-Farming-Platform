package br.com.agricoladigital.smart_farming_api.controller;

import br.com.agricoladigital.smart_farming_api.dto.request.TalhaoRequest;
import br.com.agricoladigital.smart_farming_api.dto.response.TalhaoResponse;
import br.com.agricoladigital.smart_farming_api.service.TalhaoService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/fazendas/{fazendaId}/talhoes")
@PreAuthorize("@acesso.podeAcessarFazenda(authentication, #fazendaId)")
public class TalhaoController {
    private final TalhaoService talhaoService;

    public TalhaoController(TalhaoService talhaoService) {
        this.talhaoService = talhaoService;
    }

    @PostMapping
    public ResponseEntity<TalhaoResponse> criar(
            @PathVariable Long fazendaId,
            @Valid @RequestBody TalhaoRequest request
    ) {
        TalhaoResponse response = talhaoService.criar(fazendaId, request);
        return ResponseEntity.created(
                URI.create("/api/v1/fazendas/" + fazendaId + "/talhoes/" + response.id())
        ).body(response);
    }

    @GetMapping
    public List<TalhaoResponse> listar(@PathVariable Long fazendaId) {
        return talhaoService.listarPorFazenda(fazendaId);
    }

    @GetMapping("/{talhaoId}")
    public TalhaoResponse buscar(@PathVariable Long fazendaId, @PathVariable Long talhaoId) {
        return talhaoService.buscar(fazendaId, talhaoId);
    }

    @PutMapping("/{talhaoId}")
    public TalhaoResponse atualizar(
            @PathVariable Long fazendaId,
            @PathVariable Long talhaoId,
            @Valid @RequestBody TalhaoRequest request
    ) {
        return talhaoService.atualizar(fazendaId, talhaoId, request);
    }

    @DeleteMapping("/{talhaoId}")
    public ResponseEntity<Void> excluir(@PathVariable Long fazendaId, @PathVariable Long talhaoId) {
        talhaoService.excluir(fazendaId, talhaoId);
        return ResponseEntity.noContent().build();
    }
}
