package br.com.xepamais.api.database.repository;

import br.com.xepamais.api.database.model.Feira;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeiraRepository extends JpaRepository<Feira, Integer> {
}
