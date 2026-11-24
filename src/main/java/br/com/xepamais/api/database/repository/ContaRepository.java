package br.com.xepamais.api.database.repository;

import br.com.xepamais.api.database.model.Conta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


public interface ContaRepository extends  JpaRepository<Conta, Integer>{

    List<Conta> findByAtivoTrue();
    java.util.Optional<Conta> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
}
