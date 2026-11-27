package br.com.xepamais.api.database.repository;

import br.com.xepamais.api.database.model.Produto;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProdutoRepository extends JpaRepository<Produto, Integer> {
}
