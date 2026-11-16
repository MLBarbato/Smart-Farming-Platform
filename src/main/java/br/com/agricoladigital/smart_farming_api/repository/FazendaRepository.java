package br.com.agricoladigital.smart_farming_api.repository;

import br.com.agricoladigital.smart_farming_api.entity.Fazenda;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FazendaRepository extends JpaRepository<Fazenda, Long> {
    List<Fazenda> findAllByUsuario_Id(Long usuarioId);

    boolean existsByUsuario_Id(Long usuarioId);

    List<Fazenda> findAllByLatitudeIsNotNullAndLongitudeIsNotNull();

    boolean existsByIdAndUsuario_Id(Long id, Long usuarioId);

    @org.springframework.data.jpa.repository.Query("select f.id from Fazenda f")
    List<Long> listarIds();
}
