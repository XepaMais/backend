package br.com.xepamais.api.database.repository;

import br.com.xepamais.api.database.model.CategoriaDespesa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CategoriaDespesaRepository extends JpaRepository<CategoriaDespesa, Integer> {
    Optional<CategoriaDespesa> findByNomeIgnoreCase(String nome);
}
