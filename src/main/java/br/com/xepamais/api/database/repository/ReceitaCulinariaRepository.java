package br.com.xepamais.api.database.repository;

import br.com.xepamais.api.database.model.ReceitaCulinaria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReceitaCulinariaRepository extends JpaRepository<ReceitaCulinaria, Integer> {
    Optional<ReceitaCulinaria> findByCodigo(String codigo);

    List<ReceitaCulinaria> findAllByOrderByIdAsc();
}
