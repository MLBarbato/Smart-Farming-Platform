package br.com.agricoladigital.smart_farming_api.repository;

import br.com.agricoladigital.smart_farming_api.entity.PrevisaoTempo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PrevisaoTempoRepository extends JpaRepository<PrevisaoTempo, Long> {
    boolean existsByFazenda_Id(Long fazendaId);

    List<PrevisaoTempo> findAllByFazenda_IdOrderByDataPrevisao(Long fazendaId);

    /** Remove na hora (antes de novas inserções), para não colidir com a restrição de unicidade. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from PrevisaoTempo p where p.fazenda.id = :fazendaId")
    void deleteAllByFazenda_Id(@Param("fazendaId") Long fazendaId);
}
