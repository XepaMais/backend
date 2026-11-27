package br.com.xepamais.api.database.repository;

import br.com.xepamais.api.database.model.MensagemSuporte;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MensagemSuporteRepository extends JpaRepository<MensagemSuporte, Integer> {
}
