package br.com.agricoladigital.smart_farming_api.repository;

import br.com.agricoladigital.smart_farming_api.entity.EstacaoMeteorologica;
import br.com.agricoladigital.smart_farming_api.entity.StatusDispositivo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EstacaoMeteorologicaRepository extends JpaRepository<EstacaoMeteorologica, Long> {
    @Query("""
            select e from EstacaoMeteorologica e
            join fetch e.dispositivo d
            join fetch d.fazenda
            where d.fazenda.id = :fazendaId
            """)
    Optional<EstacaoMeteorologica> buscarDaFazenda(@Param("fazendaId") Long fazendaId);

    @Query("""
            select e from EstacaoMeteorologica e
            join fetch e.dispositivo d
            join fetch d.fazenda
            where d.status = :status
            """)
    List<EstacaoMeteorologica> listarPorStatus(@Param("status") StatusDispositivo status);

    @Query("""
            select e from EstacaoMeteorologica e
            join fetch e.dispositivo d
            where d.fazenda.id in :fazendaIds
            """)
    List<EstacaoMeteorologica> listarDasFazendas(@Param("fazendaIds") Collection<Long> fazendaIds);
}
