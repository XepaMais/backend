package br.com.xepamais.api.database.repository;

import br.com.xepamais.api.database.model.DispositivoSessao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DispositivoSessaoRepository extends JpaRepository<DispositivoSessao, Integer> {

    // Aparelhos com login ativo, o usado mais recentemente primeiro.
    @Query("""
            select d from DispositivoSessao d where d.conta.id = :idConta and d.revogado = false
              and d.expiraEm > :agora order by coalesce(d.ultimoAcesso, d.dataCriacao) desc, d.id desc""")
    List<DispositivoSessao> ativas(@Param("idConta") Integer idConta, @Param("agora") LocalDateTime agora);

    Optional<DispositivoSessao> findByIdAndConta_Id(Integer id, Integer idConta);

    // "Sair dos outros aparelhos": encerra todas as sessões da conta, menos a atual.
    @Modifying
    @Query("update DispositivoSessao d set d.revogado = true where d.conta.id = :idConta and d.id <> :atual")
    int revogarOutras(@Param("idConta") Integer idConta, @Param("atual") Integer atual);
}
