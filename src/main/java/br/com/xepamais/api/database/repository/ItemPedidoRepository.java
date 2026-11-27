package br.com.xepamais.api.database.repository;

import br.com.xepamais.api.database.model.ItemPedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ItemPedidoRepository extends JpaRepository<ItemPedido, Integer> {

    @Query("""
            select i from ItemPedido i join fetch i.xepa x join fetch x.produto p join fetch p.categoria
            join fetch x.feira where i.pedido.id in :idsPedido order by i.id""")
    List<ItemPedido> dosPedidos(@Param("idsPedido") Collection<Integer> idsPedido);
}
