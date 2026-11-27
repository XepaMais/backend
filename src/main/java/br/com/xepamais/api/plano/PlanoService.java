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
import java.util.List;

// Planos do Xepa+ e o plano escolhido por cada feirante.
@Service
public class PlanoService {
    // Sem plano escolhido vale o Semente (mesma regra do front).
    public static final String PLANO_PADRAO = "gratis";

    // codigo: gratis, crescer ou pro. percentualComissao: 15.00 = 15%.
    public record PlanoResponse(String codigo, String nome, String descricao, BigDecimal valor,
                                BigDecimal percentualComissao) {}

    // escolhido = false quando o feirante ainda não escolheu (o plano mostrado é o padrão).
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
        return planoRepository.findByAtivoTrueOrderByValorAscIdAsc().stream()
                .map(PlanoService::resposta).toList();
    }

    @Transactional(readOnly = true)
    public PlanoDoFeiranteResponse doFeirante(Integer idConta) {
        Feirante feirante = feirante(idConta);
        Plano plano = feirante.getPlano();
        return new PlanoDoFeiranteResponse(plano != null, plano != null ? resposta(plano) : padrao().map(PlanoService::resposta).orElse(null));
    }

    // Troca o plano na hora. Nenhuma cobrança é feita pela API.
    @Transactional
    public PlanoDoFeiranteResponse escolher(Integer idConta, EscolhaPlanoRequest request) {
        Feirante feirante = feirante(idConta);
        String codigo = request == null || request.codigo() == null ? "" : request.codigo().trim();
        Plano plano = planoRepository.findByCodigo(codigo).filter(Plano::isAtivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Plano inválido."));
        feirante.setPlano(plano);
        return new PlanoDoFeiranteResponse(true, resposta(plano));
    }

    // Comissão que vale para o feirante agora (plano escolhido, ou o Semente).
    @Transactional(readOnly = true)
    public BigDecimal comissaoDo(Feirante feirante) {
        Plano plano = feirante.getPlano() != null ? feirante.getPlano() : padrao().orElse(null);
        return plano == null || plano.getPercentualComissao() == null ? BigDecimal.ZERO : plano.getPercentualComissao();
    }

    // Código do plano escolhido (vai no login para o front não pedir o plano de novo em outro aparelho).
    public static String codigoDo(Feirante feirante) {
        return feirante.getPlano() == null ? null : feirante.getPlano().getCodigo();
    }

    private java.util.Optional<Plano> padrao() {
        return planoRepository.findByCodigo(PLANO_PADRAO);
    }

    private Feirante feirante(Integer idConta) {
        return feiranteRepository.findByConta_Id(idConta)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Feirante não encontrado."));
    }

    private static PlanoResponse resposta(Plano plano) {
        return new PlanoResponse(plano.getCodigo(), plano.getNome(), plano.getDescricao(), plano.getValor(),
                plano.getPercentualComissao());
    }
}
