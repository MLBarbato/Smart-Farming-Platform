package br.com.agricoladigital.smart_farming_api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Regra de alerta: para um evento, um nível de criticidade tem operador, limiar e janela próprios.
 * Existe no máximo uma regra por evento e criticidade; o administrador ajusta limiar, janela,
 * descrição e se a regra está ativa, mas não a estrutura (evento, criticidade, operador, variável).
 */
@Entity
@Table(name = "regra_alerta")
@Getter
@NoArgsConstructor
public class RegraAlerta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_regra")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private EventoAlerta evento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CriticidadeAlerta criticidade;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OperadorRegra operador;

    /** Variável monitorada avaliada; nula em FALHA_SENSOR. */
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private VariavelMonitorada variavel;

    @Column(nullable = false)
    private Double limiar;

    @Column(nullable = false, length = 20)
    private String unidade;

    @Column(name = "janela_horas")
    private Integer janelaHoras;

    @Column(nullable = false)
    private Boolean ativa;

    @Column(length = 255)
    private String descricao;

    public RegraAlerta(
            EventoAlerta evento,
            CriticidadeAlerta criticidade,
            OperadorRegra operador,
            VariavelMonitorada variavel,
            Double limiar,
            String unidade,
            Integer janelaHoras,
            String descricao
    ) {
        this.evento = evento;
        this.criticidade = criticidade;
        this.operador = operador;
        this.variavel = variavel;
        this.limiar = limiar;
        this.unidade = unidade;
        this.janelaHoras = janelaHoras;
        this.ativa = true;
        this.descricao = descricao;
    }

    public void atualizar(Double limiar, Integer janelaHoras, Boolean ativa, String descricao) {
        this.limiar = limiar;
        this.janelaHoras = janelaHoras;
        this.ativa = ativa;
        this.descricao = descricao;
    }
}
