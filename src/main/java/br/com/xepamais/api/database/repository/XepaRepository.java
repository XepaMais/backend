package br.com.xepamais.api.database.repository;

import br.com.xepamais.api.database.model.Xepa;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface XepaRepository extends JpaRepository<Xepa, Integer> {

    // Vitrine do consumidor: só o que ainda tem estoque e não venceu
    @Query("""
            select x from Xepa x join fetch x.produto p join fetch p.feirante fe join fetch fe.conta
            join fetch p.categoria join fetch x.feira
            where x.status = 'DISPONIVEL' and x.quantidade > 0 and p.ativo = true
              and (x.validade is null or x.validade >= :hoje)
            order by x.dataCadastro desc, x.id desc""")
    List<Xepa> disponiveis(@Param("hoje") LocalDate hoje);

    // Xepas publicadas pelo feirante da conta
    @Query("""
            select x from Xepa x join fetch x.produto p join fetch p.categoria join fetch x.feira
            where p.feirante.conta.id = :idConta and x.status <> 'CANCELADA'
            order by x.dataCadastro desc, x.id desc""")
    List<Xepa> doFeirante(@Param("idConta") Integer idConta);

    @Query("select x from Xepa x where x.id = :id and x.produto.feirante.conta.id = :idConta")
    Optional<Xepa> doFeirante(@Param("idConta") Integer idConta, @Param("id") Integer id);

    // Trava as xepas durante o checkout para não vender o mesmo estoque duas vezes
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select x from Xepa x where x.id in :ids")
    List<Xepa> travar(@Param("ids") Collection<Integer> ids);
}
