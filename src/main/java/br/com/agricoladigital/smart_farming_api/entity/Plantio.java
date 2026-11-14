package br.com.agricoladigital.smart_farming_api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "plantio")
@Getter
@NoArgsConstructor
public class Plantio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_plantio")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_talhao", nullable = false)
    private Talhao talhao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_cultura", nullable = false)
    private Cultura cultura;

    @Column(name = "tipo_plantio", length = 100)
    private String tipoPlantio;

    @Column(nullable = false, length = 9)
    private String safra;

    @Column(name = "data_plantio", nullable = false)
    private LocalDate dataPlantio;

    @Column(name = "data_colheita_prevista")
    private LocalDate dataColheitaPrevista;

    @Column(name = "data_colheita_real")
    private LocalDate dataColheitaReal;

    @Column(name = "quantidade_colhida_kg")
    private Double quantidadeColhidaKg;

    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private StatusPlantio status;

    public Plantio(
            Talhao talhao,
            Cultura cultura,
            String tipoPlantio,
            String safra,
            LocalDate dataPlantio,
            LocalDate dataColheitaPrevista
    ) {
        this.talhao = talhao;
        this.cultura = cultura;
        this.tipoPlantio = tipoPlantio;
        this.safra = safra;
        this.dataPlantio = dataPlantio;
        this.dataColheitaPrevista = dataColheitaPrevista;
        this.status = StatusPlantio.PLANEJADO;
    }

    public void atualizar(
            Cultura cultura,
            String tipoPlantio,
            String safra,
            LocalDate dataPlantio,
            LocalDate dataColheitaPrevista
    ) {
        this.cultura = cultura;
        this.tipoPlantio = tipoPlantio;
        this.safra = safra;
        this.dataPlantio = dataPlantio;
        this.dataColheitaPrevista = dataColheitaPrevista;
    }

    public void alterarStatus(StatusPlantio novoStatus) {
        this.status = novoStatus;
    }

    public void colher(LocalDate dataColheitaReal, Double quantidadeColhidaKg) {
        this.status = StatusPlantio.COLHIDO;
        registrarColheita(dataColheitaReal, quantidadeColhidaKg);
    }

    public void registrarColheita(LocalDate dataColheitaReal, Double quantidadeColhidaKg) {
        this.dataColheitaReal = dataColheitaReal;
        this.quantidadeColhidaKg = quantidadeColhidaKg;
    }
}
