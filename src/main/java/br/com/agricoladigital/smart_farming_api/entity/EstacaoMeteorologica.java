package br.com.agricoladigital.smart_farming_api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "estacao_meteorologica")
@Getter
@NoArgsConstructor
public class EstacaoMeteorologica {

    @Id
    @Column(name = "id_dispositivo")
    private Long id;

    // Usa o mesmo ID do Dispositivo como PK e FK
    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "id_dispositivo")
    private Dispositivo dispositivo;

    @Column(length = 100)
    private String modelo;

    /** Intervalo esperado entre coletas, em minutos. */
    @Column(name = "frequencia_coleta")
    private Integer frequenciaColeta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FonteEstacao fonte;

    /** Instante (UTC) da última sincronização bem-sucedida com a fonte. */
    @Column(name = "ultima_sincronizacao")
    private LocalDateTime ultimaSincronizacao;

    public EstacaoMeteorologica(
            Dispositivo dispositivo,
            String modelo,
            Integer frequenciaColeta,
            FonteEstacao fonte
    ) {
        this.dispositivo = dispositivo;
        this.modelo = modelo;
        this.frequenciaColeta = frequenciaColeta;
        this.fonte = fonte;
    }

    public void registrarSincronizacao(LocalDateTime instanteUtc) {
        this.ultimaSincronizacao = instanteUtc;
    }
}
