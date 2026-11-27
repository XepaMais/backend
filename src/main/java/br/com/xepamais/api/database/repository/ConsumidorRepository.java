package br.com.xepamais.api.database.repository;

import br.com.xepamais.api.database.model.Consumidor;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsumidorRepository extends JpaRepository<Consumidor, Integer> {
    java.util.Optional<Consumidor> findByConta_Id(Integer contaId);

    boolean existsByConta_Id(Integer contaId);
}
