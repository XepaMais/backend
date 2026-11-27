package br.com.xepamais.api.xepinha;

import br.com.xepamais.api.database.model.Conta;
import br.com.xepamais.api.database.model.ConversaIa;
import br.com.xepamais.api.database.model.Feirante;
import br.com.xepamais.api.database.model.MensagemIa;
import br.com.xepamais.api.database.repository.ContaRepository;
import br.com.xepamais.api.database.repository.ConversaIaRepository;
import br.com.xepamais.api.database.repository.FeiranteRepository;
import br.com.xepamais.api.database.repository.MensagemIaRepository;
import br.com.xepamais.api.xepinha.ClienteIa.IaIndisponivelException;
import br.com.xepamais.api.xepinha.ClienteIa.Turno;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

// Xepinha, a assistente dos feirantes. A pergunta vai para a IA pelo servidor (com a persona e a base
// de conhecimento de resources/xepinha) e a conversa fica salva no banco.
@Service
public class XepinhaService {
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final Set<String> PLANOS_COM_IA = Set.of("crescer", "pro");
    private static final int TEXTO_MAXIMO = 4000;
    private static final int HISTORICO_MAXIMO = 30;
    // Modo demonstração (sem login): no máximo 15 perguntas a cada 10 minutos por IP.
    private static final int DEMO_LIMITE = 15;
    private static final long DEMO_JANELA_MS = 10 * 60 * 1000L;
    // Teto geral do demo (somando todos os IPs): o IP vem de cabeçalho e pode ser forjado,
    // então este limite protege a cota da API do Gemini mesmo assim.
    private static final int DEMO_LIMITE_GLOBAL = 300;
    private static final long DEMO_JANELA_GLOBAL_MS = 60 * 60 * 1000L;
    private final Deque<Long> perguntasDemoGlobal = new ArrayDeque<>();

    public static final String MSG_INDISPONIVEL = "Ops! Tive um problema ao processar sua resposta. Tente novamente em instantes.";
    public static final String MSG_SOBRECARGA = "Estou com muitas conversas agora. Tente de novo em alguns segundos.";

    // remetente: "user" (feirante) ou "ia" (Xepinha) — os mesmos nomes do front.
    public record MensagemResponse(Integer id, String remetente, String texto, LocalDateTime data) {}

    public record ConversaResponse(Integer id, String titulo, LocalDateTime criadaEm, List<MensagemResponse> mensagens) {}

    // idConversa vazio = começa uma conversa nova.
    public record PerguntaRequest(Integer idConversa, String texto) {}

    public record TurnoDemo(String remetente, String texto) {}

    public record PerguntaDemoRequest(List<TurnoDemo> historico, String texto) {}

    public record RespostaDemo(String resposta) {}

    private final ClienteIa ia;
    private final ConversaIaRepository conversaRepository;
    private final MensagemIaRepository mensagemRepository;
    private final FeiranteRepository feiranteRepository;
    private final ContaRepository contaRepository;
    private final TransactionTemplate transacao;
    private final String instrucoes;
    private final Map<String, Deque<Long>> perguntasDemo = new ConcurrentHashMap<>();

    public XepinhaService(ClienteIa ia, ConversaIaRepository conversaRepository, MensagemIaRepository mensagemRepository,
                          FeiranteRepository feiranteRepository, ContaRepository contaRepository,
                          PlatformTransactionManager transacoes) throws IOException {
        this.ia = ia;
        this.conversaRepository = conversaRepository;
        this.mensagemRepository = mensagemRepository;
        this.feiranteRepository = feiranteRepository;
        this.contaRepository = contaRepository;
        this.transacao = new TransactionTemplate(transacoes);
        this.instrucoes = recurso("xepinha/persona.md") + "\n\n## BASE DE CONHECIMENTO\n" + recurso("xepinha/base-conhecimento.md");
    }

    //  Feirante logado 

    public List<ConversaResponse> conversas(Integer idConta) {
        return transacao.execute(status -> {
            verificarPlano(idConta);
            return respostas(conversaRepository.findByConta_IdOrderByIdDesc(idConta));
        });
    }

    // Pergunta e resposta só são salvas se a Xepinha responder (se falhar, a pessoa tenta de novo).
    public ConversaResponse perguntar(Integer idConta, PerguntaRequest request) {
        String texto = texto(request == null ? null : request.texto());
        Integer idConversa = request == null ? null : request.idConversa();

        // 1) Lê o histórico (transação curta: a chamada à IA pode demorar e não segura o banco).
        List<Turno> historico = transacao.execute(status -> {
            verificarPlano(idConta);
            if (idConversa == null) return List.of();
            ConversaIa conversa = conversaRepository.findByIdAndConta_Id(idConversa, idConta)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversa não encontrada."));
            List<MensagemIa> mensagens = mensagemRepository.dasConversas(List.of(conversa.getId()));
            return mensagens.subList(Math.max(0, mensagens.size() - HISTORICO_MAXIMO), mensagens.size()).stream()
                    .map(m -> new Turno("IA".equals(m.getPapel()) ? "model" : "user", m.getTexto()))
                    .toList();
        });

        // 2) Pergunta à IA.
        String resposta = chamarIa(historico, texto);

        // 3) Salva pergunta e resposta.
        return transacao.execute(status -> {
            Conta conta = contaRepository.getReferenceById(idConta);
            ConversaIa conversa = idConversa == null ? novaConversa(conta, texto)
                    : conversaRepository.findByIdAndConta_Id(idConversa, idConta).orElseThrow();
            LocalDateTime agora = LocalDateTime.now(FUSO);
            salvarMensagem(conta, conversa, "USUARIO", texto, agora);
            salvarMensagem(conta, conversa, "IA", resposta, agora.plusNanos(1_000_000));
            return respostas(List.of(conversa)).get(0);
        });
    }

    //  Modo demonstração (sem login, nada é salvo) 

    public RespostaDemo perguntarDemo(PerguntaDemoRequest request, String ip) {
        String texto = texto(request == null ? null : request.texto());
        limitarDemo(ip);
        List<TurnoDemo> anteriores = request.historico() == null ? List.of() : request.historico();
        List<Turno> historico = anteriores.stream()
                .filter(t -> t != null && t.texto() != null && !t.texto().isBlank())
                .skip(Math.max(0, anteriores.size() - 20))
                .map(t -> new Turno("ia".equals(t.remetente()) ? "model" : "user",
                        t.texto().length() > TEXTO_MAXIMO ? t.texto().substring(0, TEXTO_MAXIMO) : t.texto()))
                .toList();
        return new RespostaDemo(chamarIa(historico, texto));
    }

    //  Auxiliares 

    private String chamarIa(List<Turno> historico, String texto) {
        try {
            return ia.responder(instrucoes, historico, texto);
        } catch (IaIndisponivelException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    ex.isSobrecarga() ? MSG_SOBRECARGA : MSG_INDISPONIVEL, ex);
        }
    }

    // A Xepinha faz parte dos planos Broto e Colheita (mesma regra do front).
    private void verificarPlano(Integer idConta) {
        Feirante feirante = feiranteRepository.findByConta_Id(idConta)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Feirante não encontrado."));
        String plano = feirante.getPlano() == null ? null : feirante.getPlano().getCodigo();
        if (plano == null || !PLANOS_COM_IA.contains(plano)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A Xepinha faz parte dos planos Broto e Colheita.");
        }
    }

    private ConversaIa novaConversa(Conta conta, String primeiraPergunta) {
        ConversaIa conversa = new ConversaIa();
        conversa.setConta(conta);
        conversa.setTitulo(primeiraPergunta.length() > 40 ? primeiraPergunta.substring(0, 40).trim() + "..." : primeiraPergunta);
        conversa.setDataCriacao(LocalDateTime.now(FUSO));
        return conversaRepository.save(conversa);
    }

    private void salvarMensagem(Conta conta, ConversaIa conversa, String papel, String texto, LocalDateTime quando) {
        MensagemIa mensagem = new MensagemIa();
        mensagem.setConta(conta);
        mensagem.setConversa(conversa);
        mensagem.setPapel(papel);
        mensagem.setTexto(texto);
        mensagem.setDataEnvio(quando);
        mensagemRepository.save(mensagem);
    }

    private List<ConversaResponse> respostas(List<ConversaIa> conversas) {
        if (conversas.isEmpty()) return List.of();
        Map<Integer, List<MensagemIa>> mensagens = mensagemRepository
                .dasConversas(conversas.stream().map(ConversaIa::getId).toList()).stream()
                .collect(Collectors.groupingBy(m -> m.getConversa().getId()));
        return conversas.stream().map(c -> new ConversaResponse(c.getId(), c.getTitulo(), c.getDataCriacao(),
                mensagens.getOrDefault(c.getId(), List.of()).stream()
                        .map(m -> new MensagemResponse(m.getId(), "IA".equals(m.getPapel()) ? "ia" : "user",
                                m.getTexto(), m.getDataEnvio()))
                        .toList())).toList();
    }

    private void limitarDemo(String ip) {
        long agora = System.currentTimeMillis();
        synchronized (perguntasDemoGlobal) {
            while (!perguntasDemoGlobal.isEmpty() && agora - perguntasDemoGlobal.peekFirst() > DEMO_JANELA_GLOBAL_MS) {
                perguntasDemoGlobal.pollFirst();
            }
            if (perguntasDemoGlobal.size() >= DEMO_LIMITE_GLOBAL) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "A demonstração da Xepinha está muito procurada agora. Tente de novo mais tarde ou entre com sua conta.");
            }
            perguntasDemoGlobal.addLast(agora);
        }
        Deque<Long> fila = perguntasDemo.computeIfAbsent(ip == null ? "?" : ip, k -> new ArrayDeque<>());
        synchronized (fila) {
            while (!fila.isEmpty() && agora - fila.peekFirst() > DEMO_JANELA_MS) fila.pollFirst();
            if (fila.size() >= DEMO_LIMITE) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Limite de perguntas da demonstração atingido. Tente de novo em alguns minutos ou entre com sua conta.");
            }
            fila.addLast(agora);
        }
        if (perguntasDemo.size() > 10_000) perguntasDemo.clear(); // evita crescer sem limite
    }

    private static String texto(String valor) {
        String limpo = valor == null ? "" : valor.trim();
        if (limpo.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escreva sua pergunta.");
        if (limpo.length() > TEXTO_MAXIMO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pergunta muito longa (máximo 4000 caracteres).");
        }
        return limpo;
    }

    private static String recurso(String caminho) throws IOException {
        return new ClassPathResource(caminho).getContentAsString(StandardCharsets.UTF_8).trim();
    }
}
