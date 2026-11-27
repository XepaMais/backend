package br.com.xepamais.api.database.repository;

import br.com.xepamais.api.database.model.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CategoriaRepository extends JpaRepository<Categoria, Integer> {
    Optional<Categoria> findByNomeIgnoreCase(String nome);
}
