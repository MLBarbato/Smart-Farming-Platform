package br.com.agricoladigital.smart_farming_api.repository;

import br.com.agricoladigital.smart_farming_api.entity.Plantio;
import br.com.agricoladigital.smart_farming_api.entity.StatusPlantio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PlantioRepository extends JpaRepository<Plantio, Long>, JpaSpecificationExecutor<Plantio> {
    boolean existsByTalhao_Id(Long talhaoId);

    boolean existsByCultura_Id(Long culturaId);

    boolean existsByTalhao_IdAndStatus(Long talhaoId, StatusPlantio status);

    Optional<Plantio> findByIdAndTalhao_Id(Long id, Long talhaoId);

    /**
     * Plantios não cancelados do talhão cujo período ocupa o intervalo [inicio, fim). O fim de um
     * plantio é a colheita real, ou a prevista; sem nenhuma das duas, o período fica em aberto
     * (representado por {@code infinito}). Colher no mesmo dia em que outro plantio começa não conflita.
     */
    @Query("""
            select p from Plantio p
            where p.talhao.id = :talhaoId
              and p.status <> :cancelado
              and p.id <> :ignorarId
              and p.dataPlantio < :fim
              and coalesce(p.dataColheitaReal, p.dataColheitaPrevista, :infinito) > :inicio
            order by p.dataPlantio
            """)
    List<Plantio> buscarConflitantes(
            @Param("talhaoId") Long talhaoId,
            @Param("ignorarId") Long ignorarId,
            @Param("inicio") LocalDate inicio,
            @Param("fim") LocalDate fim,
            @Param("infinito") LocalDate infinito,
            @Param("cancelado") StatusPlantio cancelado
    );

    /** Plantios da fazenda no estado informado, já com talhão e cultura carregados. */
    @Query("""
            select p from Plantio p
            join fetch p.cultura
            join fetch p.talhao t
            where t.fazenda.id = :fazendaId and p.status = :status
            """)
    List<Plantio> buscarPorFazendaEStatus(
            @Param("fazendaId") Long fazendaId,
            @Param("status") StatusPlantio status
    );

    /** Todos os plantios das fazendas informadas, com talhão e cultura carregados. */
    @Query("""
            select p from Plantio p
            join fetch p.cultura
            join fetch p.talhao t
            where t.fazenda.id in :fazendaIds
            """)
    List<Plantio> buscarPorFazendas(@Param("fazendaIds") Collection<Long> fazendaIds);
}
