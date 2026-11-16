package br.com.agricoladigital.smart_farming_api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Previsão diária de uma fazenda. Temperaturas em °C e precipitação em mm. */
@Entity
@Table(name = "previsao_tempo")
@Getter
@NoArgsConstructor
public class PrevisaoTempo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_previsao")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_fazenda", nullable = false)
    private Fazenda fazenda;

    @Column(name = "data_previsao", nullable = false)
    private LocalDate dataPrevisao;

    /** Temperatura média do dia. */
    @Column(name = "temperatura_prevista")
    private Double temperaturaPrevista;

    @Column(name = "temperatura_minima")
    private Double temperaturaMinima;

    @Column(name = "temperatura_maxima")
    private Double temperaturaMaxima;

    /** Precipitação acumulada prevista para o dia. */
    @Column(name = "precipitacao_prevista")
    private Double precipitacaoPrevista;

    /** Maior probabilidade de precipitação do dia, de 0 a 100. */
    @Column(name = "probabilidade_precipitacao")
    private Integer probabilidadePrecipitacao;

    @Column(name = "fonte_api", length = 100)
    private String fonteApi;

    /** Instante (UTC) em que o provedor foi consultado. */
    @Column(name = "consultada_em", nullable = false)
    private LocalDateTime consultadaEm;

    public PrevisaoTempo(
            Fazenda fazenda,
            LocalDate dataPrevisao,
            Double temperaturaPrevista,
            Double temperaturaMinima,
            Double temperaturaMaxima,
            Double precipitacaoPrevista,
            Integer probabilidadePrecipitacao,
            String fonteApi,
            LocalDateTime consultadaEm
    ) {
        this.fazenda = fazenda;
        this.dataPrevisao = dataPrevisao;
        this.temperaturaPrevista = temperaturaPrevista;
        this.temperaturaMinima = temperaturaMinima;
        this.temperaturaMaxima = temperaturaMaxima;
        this.precipitacaoPrevista = precipitacaoPrevista;
        this.probabilidadePrecipitacao = probabilidadePrecipitacao;
        this.fonteApi = fonteApi;
        this.consultadaEm = consultadaEm;
    }
}
