package br.com.agricoladigital.smart_farming_api.repository;

import br.com.agricoladigital.smart_farming_api.entity.RecomendacaoIrrigacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RecomendacaoIrrigacaoRepository
        extends JpaRepository<RecomendacaoIrrigacao, Long>, JpaSpecificationExecutor<RecomendacaoIrrigacao> {
    @Override
    @EntityGraph(attributePaths = {"talhao"})
    Page<RecomendacaoIrrigacao> findAll(Specification<RecomendacaoIrrigacao> filtro, Pageable pageable);

    @EntityGraph(attributePaths = {"talhao"})
    Optional<RecomendacaoIrrigacao> findByIdAndTalhao_Fazenda_Id(Long id, Long fazendaId);

    boolean existsByTalhao_Id(Long talhaoId);

    /** A recomendação mais recente (maior id, que cresce com a data) de cada talhão da fazenda. */
    @Query("""
            select r from RecomendacaoIrrigacao r
            join fetch r.talhao t
            where t.fazenda.id in :fazendaIds
              and r.id = (select max(r2.id) from RecomendacaoIrrigacao r2 where r2.talhao = r.talhao)
            """)
    List<RecomendacaoIrrigacao> ultimasDasFazendas(@Param("fazendaIds") java.util.Collection<Long> fazendaIds);

    default List<RecomendacaoIrrigacao> ultimasDaFazenda(Long fazendaId) {
        return ultimasDasFazendas(List.of(fazendaId));
    }
}
