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

    public Cultura(String nome, Integer cicloDias) {
        this.nome = nome;
        this.cicloDias = cicloDias;
    }

    public void atualizar(String nome, Integer cicloDias) {
        this.nome = nome;
        this.cicloDias = cicloDias;
    }
}
