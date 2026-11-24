package br.com.xepamais.api.database.repository;

import br.com.xepamais.api.database.model.Despesa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DespesaRepository extends JpaRepository<Despesa, Integer> {

    List<Despesa> findByFeirante_IdAndDataBetweenOrderByDataDescIdDesc(Integer idFeirante, LocalDate inicio, LocalDate fim);

    Optional<Despesa> findByIdAndFeirante_Id(Integer id, Integer idFeirante);
}
