package br.com.agricoladigital.smart_farming_api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "cultura")
@Getter
@NoArgsConstructor
public class Cultura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cultura")
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String nome;

    @Column(name = "ciclo_dias")
    private Integer cicloDias;

    /** Umidade do solo (%) abaixo da qual a cultura precisa de irrigação. */
    @Column(name = "umidade_minima")
    private Double umidadeMinima;

    /** Umidade do solo (%) que a irrigação deve alcançar. */
    @Column(name = "umidade_ideal")
    private Double umidadeIdeal;

    /** Profundidade efetiva das raízes, em cm (converte a diferença de umidade em lâmina de água). */
    @Column(name = "profundidade_raiz_cm")
    private Integer profundidadeRaizCm;

    public Cultura(String nome, Integer cicloDias) {
        this(nome, cicloDias, null, null, null);
    }

    public Cultura(String nome, Integer cicloDias, Double umidadeMinima, Double umidadeIdeal, Integer profundidadeRaizCm) {
        atualizar(nome, cicloDias, umidadeMinima, umidadeIdeal, profundidadeRaizCm);
    }

    public void atualizar(
            String nome, Integer cicloDias, Double umidadeMinima, Double umidadeIdeal, Integer profundidadeRaizCm
    ) {
        this.nome = nome;
        this.cicloDias = cicloDias;
        this.umidadeMinima = umidadeMinima;
        this.umidadeIdeal = umidadeIdeal;
        this.profundidadeRaizCm = profundidadeRaizCm;
    }

    public void atualizar(String nome, Integer cicloDias) {
        atualizar(nome, cicloDias, umidadeMinima, umidadeIdeal, profundidadeRaizCm);
    }

    /** Se a cultura tem os três parâmetros necessários para recomendar irrigação. */
    public boolean temParametrosDeIrrigacao() {
        return umidadeMinima != null && umidadeIdeal != null && profundidadeRaizCm != null;
    }
}
