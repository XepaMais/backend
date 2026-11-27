package br.com.xepamais.api.database.repository;

import br.com.xepamais.api.database.model.Plano;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlanoRepository extends JpaRepository<Plano, Integer> {
    Optional<Plano> findByCodigo(String codigo);

    Optional<Plano> findByNomeIgnoreCase(String nome);

    List<Plano> findByAtivoTrueOrderByValorAscIdAsc();
}
