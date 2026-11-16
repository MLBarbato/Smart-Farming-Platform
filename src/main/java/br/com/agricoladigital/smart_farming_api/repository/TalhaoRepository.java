package br.com.agricoladigital.smart_farming_api.repository;

import br.com.agricoladigital.smart_farming_api.entity.Talhao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TalhaoRepository extends JpaRepository<Talhao, Long> {
    List<Talhao> findAllByFazenda_Id(Long fazendaId);

    List<Talhao> findAllByFazenda_IdIn(Collection<Long> fazendaIds);

    Optional<Talhao> findByIdAndFazenda_Id(Long id, Long fazendaId);

    boolean existsByFazenda_Id(Long fazendaId);
}
