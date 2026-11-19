package br.com.agricoladigital.smart_farming_api.repository;

import br.com.agricoladigital.smart_farming_api.entity.Leitura;
import br.com.agricoladigital.smart_farming_api.entity.OrigemLeitura;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LeituraRepository extends JpaRepository<Leitura, Long> {
    boolean existsByDispositivo_Id(Long dispositivoId);

    /** Apaga em massa as leituras dos dispositivos informados (usado ao remover uma estação). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Leitura l where l.dispositivo.id in :dispositivoIds")
    void deleteAllByDispositivo_IdIn(@Param("dispositivoIds") Collection<Long> dispositivoIds);

    boolean existsByDispositivo_IdAndDataHora(Long dispositivoId, LocalDateTime dataHora);

    Optional<Leitura> findFirstByDispositivo_IdOrderByDataHoraDesc(Long dispositivoId);

    /** Leituras no intervalo [de, ate], da mais recente para a mais antiga; o limite vem do Pageable. */
    List<Leitura> findByDispositivo_IdAndDataHoraBetweenOrderByDataHoraDesc(
            Long dispositivoId, LocalDateTime de, LocalDateTime ate, Pageable pageable
    );

    /** A leitura mais recente de cada dispositivo informado (uma consulta só; sem leituras, não aparece). */
    @Query("""
            select l from Leitura l
            join fetch l.dispositivo
            where l.dispositivo.id in :dispositivoIds
              and l.dataHora = (select max(l2.dataHora) from Leitura l2 where l2.dispositivo.id = l.dispositivo.id)
            """)
    List<Leitura> ultimasPorDispositivos(@Param("dispositivoIds") Collection<Long> dispositivoIds);

    /** Média das leituras dos dispositivos em [de, ate]; nula se não houver nenhuma. */
    @Query("""
            select avg(l.valor) from Leitura l
            where l.dispositivo.id in :dispositivoIds and l.dataHora >= :de and l.dataHora <= :ate
            """)
    Double mediaNoIntervalo(
            @Param("dispositivoIds") Collection<Long> dispositivoIds,
            @Param("de") LocalDateTime de,
            @Param("ate") LocalDateTime ate
    );

    /** Soma das leituras dos dispositivos em [de, ate]; nula se não houver nenhuma. */
    @Query("""
            select sum(l.valor) from Leitura l
            where l.dispositivo.id in :dispositivoIds and l.dataHora >= :de and l.dataHora <= :ate
            """)
    Double somaNoIntervalo(
            @Param("dispositivoIds") Collection<Long> dispositivoIds,
            @Param("de") LocalDateTime de,
            @Param("ate") LocalDateTime ate
    );

    /** A leitura mais recente dos dispositivos em [de, ate]. */
    Optional<Leitura> findFirstByDispositivo_IdInAndDataHoraBetweenOrderByDataHoraDesc(
            Collection<Long> dispositivoIds, LocalDateTime de, LocalDateTime ate
    );

    /**
     * Substitui o valor das leituras de origem {@code origem} dos dispositivos a partir de
     * {@code desde}. Usado só pelos cenários do simulador, para que a janela das regras reflita o
     * cenário na hora.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @org.springframework.transaction.annotation.Transactional
    @Query("""
            update Leitura l set l.valor = :valor
            where l.dispositivo.id in :dispositivoIds and l.origem = :origem and l.dataHora >= :desde
            """)
    int sobrescreverValores(
            @Param("dispositivoIds") Collection<Long> dispositivoIds,
            @Param("origem") OrigemLeitura origem,
            @Param("desde") LocalDateTime desde,
            @Param("valor") double valor
    );

    /**
     * Agrega as leituras do dispositivo em [de, ate] por intervalo ({@code date_trunc} no fuso informado).
     * Cada linha: início do intervalo (época, em segundos), mínimo, média, máximo e quantidade.
     */
    @Query(value = """
            select cast(extract(epoch from (date_trunc(:unidade, (l.data_hora at time zone 'UTC') at time zone :fuso)
                                            at time zone :fuso)) as bigint),
                   min(l.valor), avg(l.valor), max(l.valor), count(*)
            from leitura l
            where l.id_dispositivo = :dispositivoId and l.data_hora >= :de and l.data_hora <= :ate
            group by 1
            order by 1
            """, nativeQuery = true)
    List<Object[]> agregar(
            @Param("dispositivoId") Long dispositivoId,
            @Param("unidade") String unidade,
            @Param("fuso") String fuso,
            @Param("de") LocalDateTime de,
            @Param("ate") LocalDateTime ate
    );

    /** Leituras dos dispositivos em [de, ate), em ordem cronológica (relatório de clima). */
    @Query("""
            select l from Leitura l
            where l.dispositivo.id in :dispositivoIds and l.dataHora >= :de and l.dataHora < :ate
            order by l.dataHora, l.id
            """)
    List<Leitura> buscarNoPeriodo(
            @Param("dispositivoIds") Collection<Long> dispositivoIds,
            @Param("de") LocalDateTime de,
            @Param("ate") LocalDateTime ate
    );

    /** Resumo das leituras de cada dispositivo em [de, ate); dispositivos sem leitura não aparecem (relatório). */
    @Query("""
            select new br.com.agricoladigital.smart_farming_api.repository.ResumoLeiturasSensor(
                l.dispositivo.id, count(l), min(l.dataHora), max(l.dataHora), min(l.valor), avg(l.valor), max(l.valor))
            from Leitura l
            where l.dispositivo.id in :dispositivoIds and l.dataHora >= :de and l.dataHora < :ate
            group by l.dispositivo.id
            """)
    List<ResumoLeiturasSensor> resumirNoPeriodo(
            @Param("dispositivoIds") Collection<Long> dispositivoIds,
            @Param("de") LocalDateTime de,
            @Param("ate") LocalDateTime ate
    );
}
