package br.com.xepamais.api.database.repository;

import br.com.xepamais.api.database.model.Feirante;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeiranteRepository extends JpaRepository<Feirante, Integer> {
    boolean existsByConta_Id(Integer contaId);
    boolean existsByCnpj(String cnpj);
    boolean existsByMatricula(String matricula);
}
