package br.com.xepamais.api.database.repository;

import br.com.xepamais.api.database.model.Avaliacao;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AvaliacaoRepository extends JpaRepository<Avaliacao, Integer> {
    Optional<Avaliacao> findByPedido_Id(Integer idPedido);

    List<Avaliacao> findByPedido_IdIn(Collection<Integer> idsPedido);

    List<Avaliacao> findByFeirante_IdOrderByDataAvaliacaoDescIdDesc(Integer idFeirante, Pageable pagina);

    @Query("select avg(a.nota) from Avaliacao a where a.feirante.id = :idFeirante")
    Double media(@Param("idFeirante") Integer idFeirante);

    long countByFeirante_Id(Integer idFeirante);
}
