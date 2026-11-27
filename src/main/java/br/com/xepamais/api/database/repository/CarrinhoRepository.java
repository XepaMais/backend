package br.com.xepamais.api.database.repository;

import br.com.xepamais.api.database.model.Carrinho;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CarrinhoRepository extends JpaRepository<Carrinho, Integer> {
    Optional<Carrinho> findByConsumidor_Id(Integer idConsumidor);
}
