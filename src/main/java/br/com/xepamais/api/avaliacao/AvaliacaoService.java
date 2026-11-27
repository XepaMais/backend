package br.com.xepamais.api.avaliacao;

import br.com.xepamais.api.consumidor.ConsumidorService;
import br.com.xepamais.api.consumidor.PedidoService;
import br.com.xepamais.api.consumidor.dto.ConsumidorDtos.AvaliacaoRequest;
import br.com.xepamais.api.consumidor.dto.ConsumidorDtos.PedidoResponse;
import br.com.xepamais.api.database.model.Avaliacao;
import br.com.xepamais.api.database.model.Consumidor;
import br.com.xepamais.api.database.model.Feira;
import br.com.xepamais.api.database.model.Feirante;
import br.com.xepamais.api.database.model.Pedido;
import br.com.xepamais.api.database.repository.AvaliacaoRepository;
import br.com.xepamais.api.database.repository.FeiranteRepository;
import br.com.xepamais.api.database.repository.PedidoRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Avaliações dos pedidos e o perfil público do feirante com a média
@Service
public class AvaliacaoService {
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final int COMENTARIO_MAXIMO = 500;
    private static final int RECENTES = 10;

    // consumidor: só o primeiro nome de quem avaliou
    public record AvaliacaoPublica(Integer nota, String comentario, LocalDateTime data, String consumidor) {}

    // Dados do feirante que o consumidor vê na página da xepa
    public record PerfilPublicoResponse(Integer idFeirante, String nome, String barraca, String foto,
                                        BigDecimal media, long totalAvaliacoes, List<AvaliacaoPublica> avaliacoes,
                                        List<FeiraDoFeirante> feiras) {}

    // Feiras onde o feirante trabalha e quem atende na barraca em cada uma
    public record FeiraDoFeirante(Integer idFeira, String nome, List<String> funcionarios) {}

    private final ConsumidorService consumidorService;
    private final PedidoService pedidoService;
    private final PedidoRepository pedidoRepository;
    private final AvaliacaoRepository avaliacaoRepository;
    private final FeiranteRepository feiranteRepository;
    private final br.com.xepamais.api.feira.FeiraService feiraService;

    public AvaliacaoService(ConsumidorService consumidorService, PedidoService pedidoService,
                            PedidoRepository pedidoRepository, AvaliacaoRepository avaliacaoRepository,
                            FeiranteRepository feiranteRepository, br.com.xepamais.api.feira.FeiraService feiraService) {
        this.feiraService = feiraService;
        this.consumidorService = consumidorService;
        this.pedidoService = pedidoService;
        this.pedidoRepository = pedidoRepository;
        this.avaliacaoRepository = avaliacaoRepository;
        this.feiranteRepository = feiranteRepository;
    }

    // O consumidor avalia depois de retirar o pedido
    @Transactional
    public PedidoResponse avaliar(Integer idConta, Integer idPedido, AvaliacaoRequest request) {
        Consumidor consumidor = consumidorService.consumidor(idConta);
        Pedido pedido = pedidoRepository.findByIdAndConsumidor_Id(idPedido, consumidor.getId()).orElse(null);
        if (pedido == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado.");
        }
        if (!"RETIRADO".equals(pedido.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Você pode avaliar depois de receber o pedido.");
        }
        if (request == null || request.nota() == null || request.nota() < 1 || request.nota() > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha uma nota de 1 a 5.");
        }
        String comentario = null;
        if (request.comentario() != null) {
            comentario = request.comentario().trim();
            if (comentario.isEmpty()) {
                comentario = null;
            }
        }
        if (comentario != null && comentario.length() > COMENTARIO_MAXIMO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Comentário muito longo (máximo 500 caracteres).");
        }

        // Se já avaliou antes, troca a nota
        Avaliacao avaliacao = avaliacaoRepository.findByPedido_Id(pedido.getId()).orElse(null);
        if (avaliacao == null) {
            avaliacao = new Avaliacao();
            avaliacao.setPedido(pedido);
            avaliacao.setConsumidor(consumidor);
            avaliacao.setFeirante(pedido.getFeirante());
        }
        avaliacao.setNota(request.nota());
        avaliacao.setComentario(comentario);
        avaliacao.setDataAvaliacao(LocalDateTime.now(FUSO));
        avaliacaoRepository.save(avaliacao);
        List<Pedido> lista = new ArrayList<>();
        lista.add(pedido);
        return pedidoService.respostas(lista).get(0);
    }

    // Público: nome, barraca, foto e avaliações do feirante
    @Transactional(readOnly = true)
    public PerfilPublicoResponse perfilPublico(Integer idContaFeirante) {
        Feirante feirante = feiranteRepository.findByConta_Id(idContaFeirante).orElse(null);
        if (feirante == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Feirante não encontrado.");
        }
        Double media = avaliacaoRepository.media(feirante.getId());
        BigDecimal mediaArredondada = null;
        if (media != null) {
            mediaArredondada = BigDecimal.valueOf(media).setScale(1, RoundingMode.HALF_UP);
        }

        List<AvaliacaoPublica> recentes = new ArrayList<>();
        for (Avaliacao a : avaliacaoRepository.findByFeirante_IdOrderByDataAvaliacaoDescIdDesc(feirante.getId(),
                PageRequest.of(0, RECENTES))) {
            recentes.add(new AvaliacaoPublica(a.getNota(), a.getComentario(), a.getDataAvaliacao(),
                    primeiroNome(a.getConsumidor().getConta().getNome())));
        }
        return new PerfilPublicoResponse(idContaFeirante, feirante.getConta().getNome(), feirante.getNomeFantasia(),
                feirante.getConta().getFoto(), mediaArredondada,
                avaliacaoRepository.countByFeirante_Id(feirante.getId()), recentes, feiras(feirante));
    }

    private List<FeiraDoFeirante> feiras(Feirante feirante) {
        Map<Integer, List<String>> funcionarios = feiraService.funcionariosPorFeira(feirante);
        List<Feira> feiras = new ArrayList<>();
        if (feirante.getFeiras() != null) {
            feiras.addAll(feirante.getFeiras());
        }

        // Ordena pelo nome da feira
        for (int i = 1; i < feiras.size(); i++) {
            int j = i;
            while (j > 0 && feiras.get(j - 1).getNome().compareTo(feiras.get(j).getNome()) > 0) {
                Feira temp = feiras.get(j);
                feiras.set(j, feiras.get(j - 1));
                feiras.set(j - 1, temp);
                j--;
            }
        }

        List<FeiraDoFeirante> lista = new ArrayList<>();
        for (Feira f : feiras) {
            List<String> nomes = funcionarios.get(f.getId());
            if (nomes == null) {
                nomes = new ArrayList<>();
            }
            lista.add(new FeiraDoFeirante(f.getId(), f.getNome(), nomes));
        }
        return lista;
    }

    private static String primeiroNome(String nome) {
        if (nome == null || nome.isBlank()) {
            return "Cliente";
        }
        return nome.trim().split("\\s+")[0];
    }
}
