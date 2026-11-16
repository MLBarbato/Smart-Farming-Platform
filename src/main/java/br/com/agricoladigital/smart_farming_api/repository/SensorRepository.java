package br.com.agricoladigital.smart_farming_api.repository;

import br.com.agricoladigital.smart_farming_api.entity.Sensor;
import br.com.agricoladigital.smart_farming_api.entity.StatusDispositivo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SensorRepository extends JpaRepository<Sensor, Long> {
    @Query("select s from Sensor s join fetch s.dispositivo d where d.talhao.id = :talhaoId order by d.codigoIdentificacao")
    List<Sensor> listarPorTalhao(@Param("talhaoId") Long talhaoId);

    @Query("select s from Sensor s join fetch s.dispositivo d where s.id = :id and d.talhao.id = :talhaoId")
    Optional<Sensor> buscarNoTalhao(@Param("id") Long id, @Param("talhaoId") Long talhaoId);

    /** Sensores de talhão no estado informado; os sensores de estação ficam de fora (têm fonte própria). */
    @Query("select s from Sensor s join fetch s.dispositivo d join fetch d.fazenda where d.status = :status and s.estacao is null")
    List<Sensor> listarPorStatus(@Param("status") StatusDispositivo status);

    @Query("select s from Sensor s join fetch s.dispositivo d where s.estacao.id = :estacaoId order by s.tipoSensor")
    List<Sensor> listarDaEstacao(@Param("estacaoId") Long estacaoId);

    @Query("select s from Sensor s join fetch s.dispositivo d where s.estacao.id in :estacaoIds")
    List<Sensor> listarDasEstacoes(@Param("estacaoIds") Collection<Long> estacaoIds);

    @Query("select s from Sensor s join fetch s.dispositivo d join fetch d.talhao t where t.fazenda.id = :fazendaId")
    List<Sensor> listarPorFazenda(@Param("fazendaId") Long fazendaId);

    @Query("select s from Sensor s join fetch s.dispositivo d join fetch d.talhao t where t.fazenda.id in :fazendaIds")
    List<Sensor> listarPorFazendas(@Param("fazendaIds") Collection<Long> fazendaIds);
}
