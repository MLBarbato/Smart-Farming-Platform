package br.com.agricoladigital.smart_farming_api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Alerta de um talhão. Todos os instantes são UTC. A cada momento há no máximo um alerta aberto
 * (ABERTO ou EM_ATENDIMENTO) por talhão e evento; isso é garantido por índice único no banco.
 */
@Entity
@Table(name = "alerta")
@Getter
@NoArgsConstructor
public class Alerta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_alerta")
    private Long id;

    /** Regra do nível de criticidade atual; muda se o alerta sobe de criticidade. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_regra", nullable = false)
    private RegraAlerta regra;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_talhao", nullable = false)
    private Talhao talhao;

    /** Leitura que originou o alerta, quando houver (previsões não têm leitura). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_leitura")
    private Leitura leitura;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private EventoAlerta evento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CriticidadeAlerta criticidade;

    /** Instante da detecção. */
    @Column(name = "data_hora", nullable = false)
    private LocalDateTime dataHora;

    @Column(nullable = false, length = 500)
    private String mensagem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusAlerta status;

    /** Usuário que assumiu, resolveu ou dispensou o alerta. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_atendente")
    private Usuario atendente;

    @Column(name = "atendido_em")
    private LocalDateTime atendidoEm;

    @Column(name = "resolvido_em")
    private LocalDateTime resolvidoEm;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ResolucaoAlerta resolucao;

    @Column(length = 500)
    private String nota;

    /** Quando o sistema constatou que a condição deixou de existir. */
    @Column(name = "normalizado_em")
    private LocalDateTime normalizadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    /** Controle de concorrência otimista (job de avaliação x atendimento). */
    @Version
    @Column(nullable = false)
    private Long versao;

    /** Cria um alerta {@code ABERTO}. */
    public Alerta(
            RegraAlerta regra,
            Talhao talhao,
            Leitura leitura,
            LocalDateTime dataHora,
            String mensagem
    ) {
        this.regra = regra;
        this.talhao = talhao;
        this.leitura = leitura;
        this.evento = regra.getEvento();
        this.criticidade = regra.getCriticidade();
        this.dataHora = dataHora;
        this.mensagem = mensagem;
        this.status = StatusAlerta.ABERTO;
        this.atualizadoEm = dataHora;
    }

    /** A condição piorou: o alerta aberto passa para a regra mais severa e a mensagem é atualizada. */
    public void escalar(RegraAlerta novaRegra, Leitura leitura, String mensagem, LocalDateTime agora) {
        this.regra = novaRegra;
        this.criticidade = novaRegra.getCriticidade();
        this.leitura = leitura;
        this.mensagem = mensagem;
        this.normalizadoEm = null;
        this.atualizadoEm = agora;
    }

    /** A condição voltou depois de ter sido dada como normalizada. */
    public void retomar(LocalDateTime agora) {
        this.normalizadoEm = null;
        this.atualizadoEm = agora;
    }

    /** A condição deixou de existir; o alerta segue com quem o atende. */
    public void registrarNormalizacao(LocalDateTime agora) {
        this.normalizadoEm = agora;
        this.atualizadoEm = agora;
    }

    /** Fecha sozinho um alerta que ninguém assumiu e cuja condição normalizou. */
    public void resolverAutomaticamente(LocalDateTime agora) {
        this.status = StatusAlerta.RESOLVIDO;
        this.resolucao = ResolucaoAlerta.AUTOMATICA;
        this.normalizadoEm = agora;
        this.resolvidoEm = agora;
        this.atualizadoEm = agora;
    }

    /** Registra quem atuou por último; o instante do primeiro atendimento é mantido. */
    private void registrarAtendimento(Usuario usuario, LocalDateTime agora, String nota) {
        this.atendente = usuario;
        if (this.atendidoEm == null) {
            this.atendidoEm = agora;
        }
        if (nota != null && !nota.isBlank()) {
            this.nota = nota.strip();
        }
        this.atualizadoEm = agora;
    }

    /** O responsável assume o alerta (ABERTO para EM_ATENDIMENTO). */
    public void assumir(Usuario usuario, LocalDateTime agora, String nota) {
        registrarAtendimento(usuario, agora, nota);
        this.status = StatusAlerta.EM_ATENDIMENTO;
    }

    /** O responsável deu o problema por tratado. */
    public void resolverManualmente(Usuario usuario, LocalDateTime agora, String nota) {
        registrarAtendimento(usuario, agora, nota);
        this.status = StatusAlerta.RESOLVIDO;
        this.resolucao = ResolucaoAlerta.MANUAL;
        this.resolvidoEm = agora;
    }

    /** O responsável considera que o alerta não procede. */
    public void dispensar(Usuario usuario, LocalDateTime agora, String nota) {
        registrarAtendimento(usuario, agora, nota);
        this.status = StatusAlerta.DISPENSADO;
        this.resolvidoEm = agora;
    }
}
