package br.com.agricoladigital.smart_farming_api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "dispositivo")
@Getter
@NoArgsConstructor
public class Dispositivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_dispositivo")
    private Long id;

    @Column(name = "codigo_identificacao", nullable = false, unique = true, length = 100)
    private String codigoIdentificacao;

    @Column(name = "tipo_dispositivo", nullable = false, length = 50)
    private String tipoDispositivo;

    @Column(name = "data_instalacao")
    private LocalDate dataInstalacao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private StatusDispositivo status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_fazenda", nullable = false)
    private Fazenda fazenda;

    /** Talhão onde o dispositivo está instalado; nulo em dispositivos da fazenda, como a estação. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_talhao")
    private Talhao talhao;

    /** Dispositivo instalado em um talhão; a fazenda é a do talhão. */
    public Dispositivo(String codigoIdentificacao, String tipoDispositivo, LocalDate dataInstalacao, StatusDispositivo status, Talhao talhao) {
        this(codigoIdentificacao, tipoDispositivo, dataInstalacao, status, talhao.getFazenda(), talhao);
    }

    /** Dispositivo da fazenda, sem talhão (estação e seus sensores). */
    public Dispositivo(String codigoIdentificacao, String tipoDispositivo, LocalDate dataInstalacao, StatusDispositivo status, Fazenda fazenda) {
        this(codigoIdentificacao, tipoDispositivo, dataInstalacao, status, fazenda, null);
    }

    private Dispositivo(String codigoIdentificacao, String tipoDispositivo, LocalDate dataInstalacao, StatusDispositivo status, Fazenda fazenda, Talhao talhao) {
        this.codigoIdentificacao = codigoIdentificacao;
        this.tipoDispositivo = tipoDispositivo;
        this.dataInstalacao = dataInstalacao;
        this.status = status;
        this.fazenda = fazenda;
        this.talhao = talhao;
    }

    public void alterarStatus(StatusDispositivo status) {
        this.status = status;
    }
}
