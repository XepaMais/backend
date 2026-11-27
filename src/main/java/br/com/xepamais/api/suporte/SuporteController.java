package br.com.xepamais.api.suporte;

import br.com.xepamais.api.auth.ContaTokenService;
import br.com.xepamais.api.auth.SessaoService;
import br.com.xepamais.api.database.model.MensagemSuporte;
import br.com.xepamais.api.database.repository.ContaRepository;
import br.com.xepamais.api.database.repository.MensagemSuporteRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

// Formulário da página "Suporte"
@RestController
@io.swagger.v3.oas.annotations.tags.Tag(name = "Suporte", description = "Mensagens de suporte")
@RequestMapping("/api/suporte")
public class SuporteController {
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    // Contra spam: no máximo 5 mensagens a cada 10 minutos por IP
    private static final int LIMITE = 5;
    private static final long JANELA_MS = 10 * 60 * 1000L;

    public record SuporteRequest(String nome, String email, String perfil, String assunto, String mensagem) {}

    public record SuporteResponse(Integer protocolo, String mensagem) {}

    private final MensagemSuporteRepository repository;
    private final ContaRepository contaRepository;
    private final SessaoService sessoes;
    private final Map<String, Deque<Long>> envios = new ConcurrentHashMap<>();

    public SuporteController(MensagemSuporteRepository repository, ContaRepository contaRepository,
                             SessaoService sessoes) {
        this.repository = repository;
        this.contaRepository = contaRepository;
        this.sessoes = sessoes;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public SuporteResponse enviar(@RequestBody SuporteRequest request, HttpServletRequest http) {
        if (request == null) throw invalido("Preencha o formulário.");
        String nome = texto(request.nome(), "Informe seu nome.", 100);
        String email = texto(request.email(), "Informe seu e-mail.", 100).toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(email).matches()) throw invalido("Informe um e-mail válido.");
        String perfil = texto(request.perfil(), "Diga se você é consumidor ou feirante.", 12).toLowerCase(Locale.ROOT);
        if (!perfil.equals("consumidor") && !perfil.equals("feirante")) {
            throw invalido("Diga se você é consumidor ou feirante.");
        }
        String assunto = texto(request.assunto(), "Informe o assunto.", 150);
        String mensagem = texto(request.mensagem(), "Escreva sua mensagem.", 5000);
        limitar(ip(http));

        MensagemSuporte suporte = new MensagemSuporte();
        suporte.setNome(nome);
        suporte.setEmail(email);
        suporte.setPerfil(perfil);
        suporte.setAssunto(assunto);
        suporte.setMensagem(mensagem);
        suporte.setDataEnvio(LocalDateTime.now(FUSO));
        // Logado: a mensagem fica ligada à conta
        String cabecalho = http.getHeader("Authorization");
        if (cabecalho != null && cabecalho.startsWith("Bearer ")) {
            ContaTokenService.DadosToken dados = sessoes.validar(cabecalho.substring(7)).orElse(null);
            if (dados != null) {
                suporte.setConta(contaRepository.getReferenceById(dados.idConta()));
            }
        }
        repository.save(suporte);
        return new SuporteResponse(suporte.getId(), "Mensagem enviada! Nossa equipe responderá pelo e-mail informado.");
    }

    private void limitar(String ip) {
        long agora = System.currentTimeMillis();
        String chave = ip;
        if (chave == null) {
            chave = "?";
        }
        envios.putIfAbsent(chave, new ArrayDeque<>());
        Deque<Long> fila = envios.get(chave);
        if (fila == null) {
            fila = new ArrayDeque<>();
        }
        synchronized (fila) {
            // Tira os envios antigos (fora da janela de tempo)
            while (!fila.isEmpty() && agora - fila.peekFirst() > JANELA_MS) {
                fila.pollFirst();
            }
            if (fila.size() >= LIMITE) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Você enviou muitas mensagens seguidas. Tente de novo em alguns minutos.");
            }
            fila.addLast(agora);
        }
        if (envios.size() > 10000) {
            envios.clear();
        }
    }

    private static String ip(HttpServletRequest http) {
        String encaminhado = http.getHeader("X-Forwarded-For");
        if (encaminhado != null && !encaminhado.isBlank()) {
            return encaminhado.split(",")[0].trim();
        }
        return http.getRemoteAddr();
    }

    private static String texto(String valor, String mensagem, int maximo) {
        String limpo = "";
        if (valor != null) {
            limpo = valor.trim();
        }
        if (limpo.isEmpty()) {
            throw invalido(mensagem);
        }
        if (limpo.length() > maximo) {
            throw invalido("Texto muito longo (máximo " + maximo + " caracteres).");
        }
        return limpo;
    }

    private static ResponseStatusException invalido(String mensagem) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
    }
}
