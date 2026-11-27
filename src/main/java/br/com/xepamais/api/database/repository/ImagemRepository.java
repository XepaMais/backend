package br.com.xepamais.api.database.repository;

import br.com.xepamais.api.database.model.Imagem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImagemRepository extends JpaRepository<Imagem, Integer> {
}
