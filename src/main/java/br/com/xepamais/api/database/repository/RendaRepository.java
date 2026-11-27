package br.com.xepamais.api.database.repository;

import br.com.xepamais.api.database.model.Renda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RendaRepository extends JpaRepository<Renda, Integer> {

    List<Renda> findByFeirante_IdAndDataBetweenOrderByDataDescIdDesc(Integer idFeirante, LocalDate inicio, LocalDate fim);

    Optional<Renda> findByIdAndFeirante_Id(Integer id, Integer idFeirante);

    // Renda automática da venda pelo app
    Optional<Renda> findByPedido_Id(Integer idPedido);

    @Query("select coalesce(sum(r.valor), 0) from Renda r where r.feirante.id = :idFeirante and r.origem.nome = :origem")
    BigDecimal somarPorOrigem(@Param("idFeirante") Integer idFeirante, @Param("origem") String origem);
}
