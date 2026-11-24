package br.com.xepamais.api.database.repository;

import br.com.xepamais.api.database.model.OrigemRenda;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrigemRendaRepository extends JpaRepository<OrigemRenda, Integer> {
    Optional<OrigemRenda> findByNomeIgnoreCase(String nome);
}
