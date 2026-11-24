package br.com.xepamais.api.database.repository;

import br.com.xepamais.api.database.model.Feirante;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FeiranteRepository extends JpaRepository<Feirante, Integer> {
    boolean existsByConta_Id(Integer contaId);
    boolean existsByCnpj(String cnpj);
    boolean existsByMatricula(String matricula);

    Optional<Feirante> findByConta_Id(Integer contaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Feirante f where f.conta.id = :contaId")
    Optional<Feirante> travarPorConta(@Param("contaId") Integer contaId);
}
