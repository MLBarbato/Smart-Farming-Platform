package br.com.agricoladigital.smart_farming_api.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "fazenda")
@Getter
@NoArgsConstructor
public class Fazenda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_fazenda")
    private Long id;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(length = 255)
    private String localizacao;

    @Column(name = "area_total", nullable = false)
    private Double areaTotal;

    private Double latitude;

    private Double longitude;

    @Column(length = 100)
    private String cidade;

    @Column(length = 2)
    private String estado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Usuario usuario;

    public Fazenda(
            String nome,
            String localizacao,
            Double areaTotal,
            Double latitude,
            Double longitude,
            String cidade,
            String estado,
            Usuario usuario
    ) {
        this.usuario = usuario;
        atualizar(nome, localizacao, areaTotal, latitude, longitude, cidade, estado);
    }

    public void atualizar(
            String nome,
            String localizacao,
            Double areaTotal,
            Double latitude,
            Double longitude,
            String cidade,
            String estado
    ) {
        this.nome = nome;
        this.localizacao = localizacao;
        this.areaTotal = areaTotal;
        this.latitude = latitude;
        this.longitude = longitude;
        this.cidade = cidade;
        this.estado = estado == null ? null : estado.toUpperCase(java.util.Locale.ROOT);
    }
}