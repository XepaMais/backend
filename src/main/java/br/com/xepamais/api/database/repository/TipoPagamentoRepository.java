package br.com.xepamais.api.database.repository;

import br.com.xepamais.api.database.model.TipoPagamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TipoPagamentoRepository extends JpaRepository<TipoPagamento, Integer> {
    Optional<TipoPagamento> findByMetodoPagamentoIgnoreCase(String metodo);
}
