package br.com.xepamais.api.auth;

import br.com.xepamais.api.auth.ContaTokenService.DadosToken;
import br.com.xepamais.api.database.model.Conta;
import br.com.xepamais.api.database.model.DispositivoSessao;
import br.com.xepamais.api.database.repository.ContaRepository;
import br.com.xepamais.api.database.repository.DispositivoSessaoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

// Sessões de login (dispositivos conectados). Cada login cria uma; encerrar a sessão desconecta o aparelho.
@Service
public class SessaoService {
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final Duration VALIDADE = Duration.ofDays(30);
    // Evita gravar no banco a cada requisição: o "último acesso" é atualizado no máximo a cada 5 min.
    private static final Duration INTERVALO_ULTIMO_ACESSO = Duration.ofMinutes(5);

    // atual = é o aparelho que está fazendo a consulta.
    public record DispositivoResponse(Integer id, String nome, String navegador, String tipo,
                                      LocalDateTime conectadoEm, LocalDateTime ultimoAcesso, boolean atual) {}

    private final DispositivoSessaoRepository repository;
    private final ContaRepository contaRepository;
    private final ContaTokenService tokens;
    private final SecureRandom random = new SecureRandom();

    public SessaoService(DispositivoSessaoRepository repository, ContaRepository contaRepository,
                         ContaTokenService tokens) {
        this.repository = repository;
        this.contaRepository = contaRepository;
        this.tokens = tokens;
    }

    // Login: registra o aparelho (pelo navegador que fez o login) e devolve o token da sessão.
    @Transactional
    public String abrir(Integer idConta, String userAgent) {
        Conta conta = contaRepository.getReferenceById(idConta);
        Aparelho aparelho = Aparelho.de(userAgent);
        LocalDateTime agora = agora();

        DispositivoSessao sessao = new DispositivoSessao();
        sessao.setConta(conta);
        sessao.setNomeAparelho(aparelho.sistema());
        sessao.setNavegador(aparelho.navegador());
        sessao.setTipoAparelho(aparelho.tipo());
        sessao.setTokenHash(identificadorAleatorio());
        sessao.setDataCriacao(agora);
        sessao.setUltimoAcesso(agora);
        sessao.setExpiraEm(agora.plus(VALIDADE));
        repository.save(sessao);
        return tokens.gerar(idConta, sessao.getId(), sessao.getExpiraEm().atZone(FUSO).toInstant());
    }

    // Usado em toda requisição de rota da conta: token assinado + sessão ainda ativa.
    @Transactional
    public Optional<DadosToken> validar(String token) {
        Optional<DadosToken> dados = tokens.ler(token);
        if (dados.isEmpty()) return Optional.empty();
        Optional<DispositivoSessao> sessao = repository.findByIdAndConta_Id(dados.get().idSessao(), dados.get().idConta());
        LocalDateTime agora = agora();
        if (sessao.isEmpty() || sessao.get().isRevogado() || !sessao.get().getExpiraEm().isAfter(agora)) {
            return Optional.empty();
        }
        DispositivoSessao ativa = sessao.get();
        if (ativa.getUltimoAcesso() == null || ativa.getUltimoAcesso().plus(INTERVALO_ULTIMO_ACESSO).isBefore(agora)) {
            ativa.setUltimoAcesso(agora);
        }
        return dados;
    }

    @Transactional(readOnly = true)
    public List<DispositivoResponse> listar(Integer idConta, Integer idSessaoAtual) {
        return repository.ativas(idConta, agora()).stream()
                .map(s -> new DispositivoResponse(s.getId(), s.getNomeAparelho(), s.getNavegador(),
                        s.getTipoAparelho() == null ? "computador" : s.getTipoAparelho(),
                        s.getDataCriacao(), s.getUltimoAcesso(), s.getId().equals(idSessaoAtual)))
                .toList();
    }

    // Desconecta um aparelho (o próprio aparelho também: é o mesmo que sair).
    @Transactional
    public void encerrar(Integer idConta, Integer idSessao) {
        DispositivoSessao sessao = repository.findByIdAndConta_Id(idSessao, idConta)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dispositivo não encontrado."));
        sessao.setRevogado(true);
    }

    @Transactional
    public int encerrarOutras(Integer idConta, Integer idSessaoAtual) {
        return repository.revogarOutras(idConta, idSessaoAtual);
    }

    private String identificadorAleatorio() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception ex) {
            throw new IllegalStateException("Não foi possível criar a sessão.", ex);
        }
    }

    private static LocalDateTime agora() {
        return LocalDateTime.now(FUSO);
    }

    // Lê do cabeçalho User-Agent o sistema, o navegador e o tipo do aparelho (mesma lógica da tela).
    record Aparelho(String sistema, String navegador, String tipo) {
        static Aparelho de(String agente) {
            String ua = agente == null ? "" : agente;
            String sistema = "Sistema não identificado";
            String tipo = "computador";
            if (ua.contains("iPad")) { sistema = "iPad"; tipo = "tablet"; }
            else if (ua.contains("iPhone")) { sistema = "iPhone"; tipo = "celular"; }
            else if (ua.contains("Android")) { sistema = "Android"; tipo = ua.contains("Mobile") ? "celular" : "tablet"; }
            else if (ua.contains("Windows")) sistema = "Windows";
            else if (ua.contains("Mac OS")) sistema = "macOS";
            else if (ua.contains("Linux")) sistema = "Linux";

            String navegador = "Navegador não identificado";
            if (ua.contains("Edg/")) navegador = "Microsoft Edge";
            else if (ua.contains("OPR/")) navegador = "Opera";
            else if (ua.contains("Firefox/")) navegador = "Firefox";
            else if (ua.contains("Chrome/")) navegador = "Google Chrome";
            else if (ua.contains("Safari/")) navegador = "Safari";
            return new Aparelho(sistema, navegador, tipo);
        }
    }
}
