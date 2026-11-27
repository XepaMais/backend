package br.com.xepamais.api.database.repository;

import br.com.xepamais.api.database.model.IngredienteReceita;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IngredienteReceitaRepository extends JpaRepository<IngredienteReceita, Integer> {
    List<IngredienteReceita> findAllByOrderByIdAsc();

    List<IngredienteReceita> findByReceita_IdOrderByIdAsc(Integer idReceita);
}
