package br.com.xepamais.api.plano;

import br.com.xepamais.api.database.model.Plano;
import br.com.xepamais.api.database.repository.PlanoRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class PlanosIniciais implements ApplicationRunner {

    private record Padrao(String codigo, String nome, String valor, String comissao, String descricao) {}

    private static final List<Padrao> PLANOS = List.of(
            new Padrao("gratis", "Semente", "0.00", "15.00",
                    "Ideal para quem está começando agora e quer dar os primeiros passos sem nenhum investimento inicial."),
            new Padrao("crescer", "Broto", "29.90", "8.00",
                    "Perfeito para negócios em expansão que precisam de mais flexibilidade e recursos avançados."),
            new Padrao("pro", "Colheita", "59.90", "5.00",
                    "A solução completa para feirante que busca alta performance, controle total e máxima produtividade."));

    private final PlanoRepository planoRepository;

    public PlanosIniciais(PlanoRepository planoRepository) {
        this.planoRepository = planoRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (Padrao padrao : PLANOS) {
            if (planoRepository.findByCodigo(padrao.codigo()).isPresent()) {
                continue;
            }
            // Se já tem um plano com o mesmo nome, só coloca o código nele
            Plano plano = planoRepository.findByNomeIgnoreCase(padrao.nome()).orElse(null);
            if (plano == null) {
                plano = new Plano();
                plano.setNome(padrao.nome());
                plano.setValor(new BigDecimal(padrao.valor()));
                plano.setPercentualComissao(new BigDecimal(padrao.comissao()));
                plano.setDescricao(padrao.descricao());
                plano.setAtivo(true);
            }
            plano.setCodigo(padrao.codigo());
            planoRepository.save(plano);
        }
    }
}
