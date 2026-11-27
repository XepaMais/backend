package br.com.xepamais.api.database.repository;

import br.com.xepamais.api.database.model.Chat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ChatRepository extends JpaRepository<Chat, Integer> {
    List<Chat> findByConsumidor_IdOrderByIdDesc(Integer idConsumidor);

    List<Chat> findByFeirante_IdOrderByIdDesc(Integer idFeirante);

    Optional<Chat> findByPedido_Id(Integer idPedido);

    List<Chat> findByPedido_IdIn(Collection<Integer> idsPedido);

    // Dúvida sobre a xepa ainda aberta (reaproveitada em vez de abrir outra).
    @Query("""
            select c from Chat c where c.consumidor.id = :idConsumidor and c.xepa.id = :idXepa
              and c.pedido is null and c.status = 'RECENTE'""")
    Optional<Chat> duvidaAberta(@Param("idConsumidor") Integer idConsumidor, @Param("idXepa") Integer idXepa);
}
