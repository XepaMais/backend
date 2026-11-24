package br.com.xepamais.api.database.repository;

import br.com.xepamais.api.database.model.Saque;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface SaqueRepository extends JpaRepository<Saque, Integer> {

    List<Saque> findByFeirante_IdOrderByDataSolicitacaoDescIdDesc(Integer idFeirante);

    @Query("select coalesce(sum(s.valor), 0) from Saque s where s.feirante.id = :idFeirante and s.status <> 'RECUSADO'")
    BigDecimal somarNaoRecusados(@Param("idFeirante") Integer idFeirante);
}
