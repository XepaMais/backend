package br.com.xepamais.api.database.repository;

import br.com.xepamais.api.database.model.ConversaIa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConversaIaRepository extends JpaRepository<ConversaIa, Integer> {
    List<ConversaIa> findByConta_IdOrderByIdDesc(Integer idConta);

    Optional<ConversaIa> findByIdAndConta_Id(Integer id, Integer idConta);
}
