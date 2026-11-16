package br.com.agricoladigital.smart_farming_api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Recomendação de irrigação de um talhão. Guarda o resultado, a justificativa e uma cópia dos
 * insumos do cálculo (parâmetros da cultura, umidade, chuva e temperatura previstas), porque a
 * cultura e a previsão mudam depois. Instantes em UTC.
 */
@Entity
@Table(name = "recomendacao_irrigacao")
@Getter
@NoArgsConstructor
public class RecomendacaoIrrigacao {

    private static final String SEPARADOR = ";";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_recomendacao")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_talhao", nullable = false)
    private Talhao talhao;

    /** Primeira linha da previsão usada; é só uma referência (a previsão é substituída a cada atualização). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_previsao")
    private PrevisaoTempo previsao;

    @Column(name = "data_hora", nullable = false)
    private LocalDateTime dataHora;

    /** Até quando a recomendação vale; depois disso o job calcula de novo. */
    @Column(name = "valido_ate", nullable = false)
    private LocalDateTime validoAte;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SituacaoIrrigacao situacao;

    /** Lâmina sugerida em mm; só existe quando a situação é IRRIGAR. */
    @Column(name = "volume_recomendado_mm")
    private Double volumeRecomendadoMm;

    @Column(length = 500)
    private String justificativa;

    @Column(name = "cultura_nome", length = 100)
    private String culturaNome;

    @Column(name = "umidade_minima")
    private Double umidadeMinima;

    @Column(name = "umidade_ideal")
    private Double umidadeIdeal;

    @Column(name = "profundidade_raiz_cm")
    private Integer profundidadeRaizCm;

    @Column(name = "umidade_atual")
    private Double umidadeAtual;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem_umidade", length = 30)
    private OrigemUmidade origemUmidade;

    @Column(name = "deficit_mm")
    private Double deficitMm;

    @Column(name = "chuva_prevista_mm")
    private Double chuvaPrevistaMm;

    @Column(name = "chuva_efetiva_mm")
    private Double chuvaEfetivaMm;

    @Column(name = "temperatura_maxima_prevista")
    private Double temperaturaMaximaPrevista;

    @Column(name = "ajuste_temperatura", nullable = false)
    private boolean ajusteTemperatura;

    @Column(name = "previsao_consultada_em")
    private LocalDateTime previsaoConsultadaEm;

    @Column(name = "previsao_fonte", length = 100)
    private String previsaoFonte;

    /** O que faltou para recomendar, separado por ';' (só em SEM_DADOS_SUFICIENTES). */
    @Column(name = "dados_faltantes", length = 255)
    private String dadosFaltantes;

    public RecomendacaoIrrigacao(
            Talhao talhao,
            PrevisaoTempo previsao,
            LocalDateTime dataHora,
            LocalDateTime validoAte,
            SituacaoIrrigacao situacao,
            Double volumeRecomendadoMm,
            String justificativa,
            InsumosIrrigacao insumos
    ) {
        this.talhao = talhao;
        this.previsao = previsao;
        this.previsaoConsultadaEm = previsao == null ? null : previsao.getConsultadaEm();
        this.previsaoFonte = previsao == null ? null : previsao.getFonteApi();
        this.dataHora = dataHora;
        this.validoAte = validoAte;
        this.situacao = situacao;
        this.volumeRecomendadoMm = volumeRecomendadoMm;
        this.justificativa = justificativa;
        this.culturaNome = insumos.culturaNome();
        this.umidadeMinima = insumos.umidadeMinima();
        this.umidadeIdeal = insumos.umidadeIdeal();
        this.profundidadeRaizCm = insumos.profundidadeRaizCm();
        this.umidadeAtual = insumos.umidadeAtual();
        this.origemUmidade = insumos.origemUmidade();
        this.deficitMm = insumos.deficitMm();
        this.chuvaPrevistaMm = insumos.chuvaPrevistaMm();
        this.chuvaEfetivaMm = insumos.chuvaEfetivaMm();
        this.temperaturaMaximaPrevista = insumos.temperaturaMaximaPrevista();
        this.ajusteTemperatura = insumos.ajusteTemperatura();
        this.dadosFaltantes = insumos.dadosFaltantes() == null || insumos.dadosFaltantes().isEmpty()
                ? null : String.join(SEPARADOR, insumos.dadosFaltantes());
    }

    public List<String> listaDadosFaltantes() {
        return dadosFaltantes == null ? List.of() : List.of(dadosFaltantes.split(SEPARADOR));
    }
}
