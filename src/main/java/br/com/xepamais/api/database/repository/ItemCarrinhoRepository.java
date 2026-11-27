package br.com.xepamais.api.database.repository;

import br.com.xepamais.api.database.model.ItemCarrinho;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ItemCarrinhoRepository extends JpaRepository<ItemCarrinho, Integer> {
    List<ItemCarrinho> findByCarrinho_IdOrderByIdAsc(Integer idCarrinho);

    Optional<ItemCarrinho> findByCarrinho_IdAndXepa_Id(Integer idCarrinho, Integer idXepa);

    @Modifying
    @Query("delete from ItemCarrinho i where i.carrinho.id = :idCarrinho")
    void esvaziar(@Param("idCarrinho") Integer idCarrinho);

    @Modifying
    @Query("delete from ItemCarrinho i where i.carrinho.id = :idCarrinho and i.xepa.id in :idsXepa")
    void removerXepas(@Param("idCarrinho") Integer idCarrinho, @Param("idsXepa") Collection<Integer> idsXepa);
}
