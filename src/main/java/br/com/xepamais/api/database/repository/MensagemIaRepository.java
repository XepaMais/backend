package br.com.xepamais.api.database.repository;

import br.com.xepamais.api.database.model.MensagemIa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface MensagemIaRepository extends JpaRepository<MensagemIa, Integer> {
    @Query("select m from MensagemIa m where m.conversa.id in :ids order by m.dataEnvio, m.id")
    List<MensagemIa> dasConversas(@Param("ids") Collection<Integer> ids);
}
