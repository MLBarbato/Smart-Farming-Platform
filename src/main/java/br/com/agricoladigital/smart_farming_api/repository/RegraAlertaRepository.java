package br.com.agricoladigital.smart_farming_api.repository;

import br.com.agricoladigital.smart_farming_api.entity.EventoAlerta;
import br.com.agricoladigital.smart_farming_api.entity.RegraAlerta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RegraAlertaRepository extends JpaRepository<RegraAlerta, Long> {
    List<RegraAlerta> findAllByEvento(EventoAlerta evento);
}
