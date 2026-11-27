package br.com.xepamais.api.database.repository;

import br.com.xepamais.api.database.model.FuncionarioFeira;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FuncionarioFeiraRepository extends JpaRepository<FuncionarioFeira, Integer> {
    List<FuncionarioFeira> findByFeirante_IdOrderByIdAsc(Integer idFeirante);

    @Modifying
    @Query("delete from FuncionarioFeira f where f.feirante.id = :idFeirante and f.feira.id = :idFeira")
    void apagar(@Param("idFeirante") Integer idFeirante, @Param("idFeira") Integer idFeira);
}
