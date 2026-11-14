package br.com.agricoladigital.smart_farming_api.repository;

import br.com.agricoladigital.smart_farming_api.entity.PrevisaoTempo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrevisaoTempoRepository extends JpaRepository<PrevisaoTempo, Long> {
    boolean existsByFazenda_Id(Long fazendaId);
}
