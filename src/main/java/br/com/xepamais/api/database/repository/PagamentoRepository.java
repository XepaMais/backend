package br.com.xepamais.api.database.repository;

import br.com.xepamais.api.database.model.Pagamento;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PagamentoRepository extends JpaRepository<Pagamento, Integer> {
}
