package br.com.xepamais.api.database.repository;

import br.com.xepamais.api.database.model.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PedidoRepository extends JpaRepository<Pedido, Integer> {
    List<Pedido> findByConsumidor_IdOrderByDataPedidoDescIdDesc(Integer idConsumidor);

    List<Pedido> findByFeirante_IdOrderByIdDesc(Integer idFeirante);

    Optional<Pedido> findByIdAndConsumidor_Id(Integer id, Integer idConsumidor);
}
