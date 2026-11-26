package br.com.xepamais.api.database.repository;

import br.com.xepamais.api.database.model.Feira;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FeiraRepository extends JpaRepository<Feira, Integer> {

    // Feiras ligadas ao feirante da conta (tb_feirante_feira).
    @Query("select f from Feirante fe join fe.feiras f where fe.conta.id = :idConta order by f.nome")
    List<Feira> doFeirante(@Param("idConta") Integer idConta);

    @Query("select f from Feirante fe join fe.feiras f where fe.conta.id = :idConta and f.id = :idFeira")
    Optional<Feira> doFeirante(@Param("idConta") Integer idConta, @Param("idFeira") Integer idFeira);
}
