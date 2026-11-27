package br.com.xepamais.api.database.repository;

import br.com.xepamais.api.database.model.Mensagem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface MensagemRepository extends JpaRepository<Mensagem, Integer> {
    @Query("select m from Mensagem m where m.chat.id in :idsChat order by m.dataEnvio, m.id")
    List<Mensagem> dosChats(@Param("idsChat") Collection<Integer> idsChat);
}
