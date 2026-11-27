package br.com.xepamais.api.plano;

import br.com.xepamais.api.database.model.Feirante;
import br.com.xepamais.api.database.model.Plano;
import br.com.xepamais.api.database.repository.FeiranteRepository;
import br.com.xepamais.api.database.repository.PlanoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

// Planos do Xepa+ e o plano escolhido por cada feirante
@Service
public class PlanoService {
    // Sem plano escolhido vale o Semente
    public static final String PLANO_PADRAO = "gratis";

    // codigo: gratis, crescer ou pro. percentualComissao: 15.00 = 15%
    public record PlanoResponse(String codigo, String nome, String descricao, BigDecimal valor,
                                BigDecimal percentualComissao) {}

    // escolhido = false quando o feirante ainda não escolheu
    public record PlanoDoFeiranteResponse(boolean escolhido, PlanoResponse plano) {}

    public record EscolhaPlanoRequest(String codigo) {}

    private final PlanoRepository planoRepository;
    private final FeiranteRepository feiranteRepository;

    public PlanoService(PlanoRepository planoRepository, FeiranteRepository feiranteRepository) {
        this.planoRepository = planoRepository;
        this.feiranteRepository = feiranteRepository;
    }

    @Transactional(readOnly = true)
    public List<PlanoResponse> listar() {
        List<PlanoResponse> lista = new ArrayList<>();
        for (Plano plano : planoRepository.findByAtivoTrueOrderByValorAscIdAsc()) {
            lista.add(resposta(plano));
        }
        return lista;
    }

    @Transactional(readOnly = true)
    public PlanoDoFeiranteResponse doFeirante(Integer idConta) {
        Feirante feirante = feirante(idConta);
        Plano plano = feirante.getPlano();
        if (plano != null) {
            return new PlanoDoFeiranteResponse(true, resposta(plano));
        }
        // Ainda não escolheu: mostra o plano padrão
        Plano padrao = padrao();
        if (padrao == null) {
            return new PlanoDoFeiranteResponse(false, null);
        }
        return new PlanoDoFeiranteResponse(false, resposta(padrao));
    }

    // Troca o plano na hora
    @Transactional
    public PlanoDoFeiranteResponse escolher(Integer idConta, EscolhaPlanoRequest request) {
        Feirante feirante = feirante(idConta);
        String codigo = "";
        if (request != null && request.codigo() != null) {
            codigo = request.codigo().trim();
        }
        Plano plano = planoRepository.findByCodigo(codigo).orElse(null);
        if (plano == null || !plano.isAtivo()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Plano inválido.");
        }
        feirante.setPlano(plano);
        return new PlanoDoFeiranteResponse(true, resposta(plano));
    }

    // Comissão que vale para o feirante agora
    @Transactional(readOnly = true)
    public BigDecimal comissaoDo(Feirante feirante) {
        Plano plano = feirante.getPlano();
        if (plano == null) {
            plano = padrao();
        }
        if (plano == null || plano.getPercentualComissao() == null) {
            return BigDecimal.ZERO;
        }
        return plano.getPercentualComissao();
    }

    // Código do plano escolhido
    public static String codigoDo(Feirante feirante) {
        if (feirante.getPlano() == null) {
            return null;
        }
        return feirante.getPlano().getCodigo();
    }

    private Plano padrao() {
        return planoRepository.findByCodigo(PLANO_PADRAO).orElse(null);
    }

    private Feirante feirante(Integer idConta) {
        Feirante feirante = feiranteRepository.findByConta_Id(idConta).orElse(null);
        if (feirante == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Feirante não encontrado.");
        }
        return feirante;
    }

    private static PlanoResponse resposta(Plano plano) {
        return new PlanoResponse(plano.getCodigo(), plano.getNome(), plano.getDescricao(), plano.getValor(),
                plano.getPercentualComissao());
    }
}
