package br.com.agricoladigital.smart_farming_api.repository;

import br.com.agricoladigital.smart_farming_api.entity.RecomendacaoIrrigacao;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecomendacaoIrrigacaoRepository extends JpaRepository<RecomendacaoIrrigacao, Long> {
    boolean existsByTalhao_Id(Long talhaoId);
}
