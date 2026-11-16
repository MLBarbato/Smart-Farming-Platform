package br.com.agricoladigital.smart_farming_api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "sensor")
@Getter
@NoArgsConstructor
public class Sensor {

    @Id
    @Column(name = "id_dispositivo")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId // Usa o mesmo ID do Dispositivo como PK e FK do Sensor
    @JoinColumn(name = "id_dispositivo")
    private Dispositivo dispositivo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_sensor", nullable = false, length = 100)
    private VariavelMonitorada tipoSensor;

    @Column(name = "unidade_medida", nullable = false, length = 50)
    private String unidadeMedida;

    /** Estação a que o sensor pertence; nulo nos sensores de talhão. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_estacao")
    private EstacaoMeteorologica estacao;

    public Sensor(
            Dispositivo dispositivo,
            VariavelMonitorada tipoSensor
    ) {
        this.dispositivo = dispositivo;
        this.tipoSensor = tipoSensor;
        this.unidadeMedida = tipoSensor.getUnidade();
    }

    /** Sensor de uma estação (sem talhão). */
    public Sensor(Dispositivo dispositivo, VariavelMonitorada tipoSensor, EstacaoMeteorologica estacao) {
        this(dispositivo, tipoSensor);
        this.estacao = estacao;
    }
}