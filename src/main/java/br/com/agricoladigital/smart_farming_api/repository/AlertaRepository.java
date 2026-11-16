package br.com.agricoladigital.smart_farming_api.repository;

import br.com.agricoladigital.smart_farming_api.entity.Alerta;
import br.com.agricoladigital.smart_farming_api.entity.StatusAlerta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AlertaRepository extends JpaRepository<Alerta, Long>, JpaSpecificationExecutor<Alerta> {
    /** Busca paginada com tudo o que a resposta precisa já carregado (sem consultas extras por linha). */
    @Override
    @EntityGraph(attributePaths = {"talhao", "regra", "atendente", "leitura", "leitura.dispositivo"})
    Page<Alerta> findAll(Specification<Alerta> filtro, Pageable pageable);

    @EntityGraph(attributePaths = {"talhao", "regra", "atendente", "leitura", "leitura.dispositivo"})
    Optional<Alerta> findByIdAndTalhao_Fazenda_Id(Long id, Long fazendaId);

    boolean existsByTalhao_Id(Long talhaoId);

    /** Alertas da fazenda nos estados informados, com regra e talhão carregados. */
    @Query("""
            select a from Alerta a
            join fetch a.regra
            join fetch a.talhao t
            where t.fazenda.id = :fazendaId and a.status in :status
            """)
    List<Alerta> buscarDaFazendaPorStatus(
            @Param("fazendaId") Long fazendaId,
            @Param("status") Collection<StatusAlerta> status
    );

    /** Alertas da fazenda fechados (nos estados informados) a partir de {@code desde}. */
    @Query("""
            select a from Alerta a
            join fetch a.talhao t
            where t.fazenda.id = :fazendaId and a.status in :status and a.resolvidoEm >= :desde
            """)
    List<Alerta> buscarFechadosDesde(
            @Param("fazendaId") Long fazendaId,
            @Param("status") Collection<StatusAlerta> status,
            @Param("desde") LocalDateTime desde
    );

    /** Alertas nos estados informados das fazendas, contados por talhão e criticidade. */
    @Query("""
            select new br.com.agricoladigital.smart_farming_api.repository.ContagemAlerta(
                t.fazenda.id, t.id, a.criticidade, count(a))
            from Alerta a join a.talhao t
            where t.fazenda.id in :fazendaIds and a.status in :status
            group by t.fazenda.id, t.id, a.criticidade
            """)
    List<ContagemAlerta> contarPorTalhaoECriticidade(
            @Param("fazendaIds") Collection<Long> fazendaIds,
            @Param("status") Collection<StatusAlerta> status
    );
}
