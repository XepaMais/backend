package br.com.xepamais.api.database.repository;

import br.com.xepamais.api.database.model.Pagamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PagamentoRepository extends JpaRepository<Pagamento, Integer> {
    List<Pagamento> findByPedido_Id(Integer idPedido);
}
