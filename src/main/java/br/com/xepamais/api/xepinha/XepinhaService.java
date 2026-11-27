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

// Xepinha, a assistente dos feirantes
@Service
public class XepinhaService {
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final Set<String> PLANOS_COM_IA = Set.of("crescer", "pro");
    private static final int TEXTO_MAXIMO = 4000;
    private static final int HISTORICO_MAXIMO = 30;
    // Modo demonstração: no máximo 15 perguntas a cada 10 minutos por IP
    private static final int DEMO_LIMITE = 15;
    private static final long DEMO_JANELA_MS = 10 * 60 * 1000L;
    // Teto geral do demo
    private static final int DEMO_LIMITE_GLOBAL = 300;
    private static final long DEMO_JANELA_GLOBAL_MS = 60 * 60 * 1000L;
    private final Deque<Long> perguntasDemoGlobal = new ArrayDeque<>();

    public static final String MSG_INDISPONIVEL = "Ops! Tive um problema ao processar sua resposta. Tente novamente em instantes.";
    public static final String MSG_SOBRECARGA = "Estou com muitas conversas agora. Tente de novo em alguns segundos.";

    // remetente: "user" ou "ia" — os mesmos nomes do front
    public record MensagemResponse(Integer id, String remetente, String texto, LocalDateTime data) {}

    public record ConversaResponse(Integer id, String titulo, LocalDateTime criadaEm, List<MensagemResponse> mensagens) {}

    // idConversa vazio = começa uma conversa nova
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

    // Feirante logado

    public List<ConversaResponse> conversas(Integer idConta) {
        return transacao.execute(status -> {
            verificarPlano(idConta);
            return respostas(conversaRepository.findByConta_IdOrderByIdDesc(idConta));
        });
    }

    // Pergunta e resposta só são salvas se a Xepinha responder
    public ConversaResponse perguntar(Integer idConta, PerguntaRequest request) {
        String pergunta = null;
        if (request != null) {
            pergunta = request.texto();
        }
        String texto = texto(pergunta);
        // Se o request fosse null, o texto() já teria dado erro
        Integer idConversa = request.idConversa();

        // 1) Lê o histórico
        List<Turno> historico = transacao.execute(status -> {
            verificarPlano(idConta);
            List<Turno> turnos = new ArrayList<>();
            if (idConversa == null) {
                return turnos;
            }
            ConversaIa conversa = conversaRepository.findByIdAndConta_Id(idConversa, idConta).orElse(null);
            if (conversa == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversa não encontrada.");
            }
            List<MensagemIa> mensagens = mensagemRepository.dasConversas(List.of(conversa.getId()));
            // Só as últimas mensagens vão para a IA
            int comeco = mensagens.size() - HISTORICO_MAXIMO;
            if (comeco < 0) {
                comeco = 0;
            }
            for (int i = comeco; i < mensagens.size(); i++) {
                MensagemIa m = mensagens.get(i);
                if ("IA".equals(m.getPapel())) {
                    turnos.add(new Turno("model", m.getTexto()));
                } else {
                    turnos.add(new Turno("user", m.getTexto()));
                }
            }
            return turnos;
        });

        // 2) Pergunta à IA
        String resposta = chamarIa(historico, texto);

        // 3) Salva pergunta e resposta
        return transacao.execute(status -> {
            Conta conta = contaRepository.getReferenceById(idConta);
            ConversaIa conversa;
            if (idConversa == null) {
                conversa = novaConversa(conta, texto);
            } else {
                conversa = conversaRepository.findByIdAndConta_Id(idConversa, idConta).orElseThrow();
            }
            LocalDateTime agora = LocalDateTime.now(FUSO);
            salvarMensagem(conta, conversa, "USUARIO", texto, agora);
            salvarMensagem(conta, conversa, "IA", resposta, agora.plusNanos(1_000_000));
            return respostas(List.of(conversa)).get(0);
        });
    }

    // Modo demonstração

    public RespostaDemo perguntarDemo(PerguntaDemoRequest request, String ip) {
        String pergunta = null;
        if (request != null) {
            pergunta = request.texto();
        }
        String texto = texto(pergunta);
        limitarDemo(ip);
        List<TurnoDemo> anteriores = new ArrayList<>();
        if (request.historico() != null) {
            anteriores = request.historico();
        }

        // Tira as mensagens vazias
        List<TurnoDemo> validos = new ArrayList<>();
        for (TurnoDemo t : anteriores) {
            if (t != null && t.texto() != null && !t.texto().isBlank()) {
                validos.add(t);
            }
        }

        // Manda só as últimas 20
        int pular = anteriores.size() - 20;
        if (pular < 0) {
            pular = 0;
        }
        List<Turno> historico = new ArrayList<>();
        for (int i = pular; i < validos.size(); i++) {
            TurnoDemo t = validos.get(i);
            String textoTurno = t.texto();
            if (textoTurno.length() > TEXTO_MAXIMO) {
                textoTurno = textoTurno.substring(0, TEXTO_MAXIMO);
            }
            if ("ia".equals(t.remetente())) {
                historico.add(new Turno("model", textoTurno));
            } else {
                historico.add(new Turno("user", textoTurno));
            }
        }
        return new RespostaDemo(chamarIa(historico, texto));
    }

    // Auxiliares

    private String chamarIa(List<Turno> historico, String texto) {
        try {
            return ia.responder(instrucoes, historico, texto);
        } catch (IaIndisponivelException ex) {
            String mensagem = MSG_INDISPONIVEL;
            if (ex.isSobrecarga()) {
                mensagem = MSG_SOBRECARGA;
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, mensagem, ex);
        }
    }

    // A Xepinha faz parte dos planos Broto e Colheita
    private void verificarPlano(Integer idConta) {
        Feirante feirante = feiranteRepository.findByConta_Id(idConta).orElse(null);
        if (feirante == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Feirante não encontrado.");
        }
        String plano = null;
        if (feirante.getPlano() != null) {
            plano = feirante.getPlano().getCodigo();
        }
        if (plano == null || !PLANOS_COM_IA.contains(plano)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A Xepinha faz parte dos planos Broto e Colheita.");
        }
    }

    private ConversaIa novaConversa(Conta conta, String primeiraPergunta) {
        ConversaIa conversa = new ConversaIa();
        conversa.setConta(conta);
        String titulo = primeiraPergunta;
        if (titulo.length() > 40) {
            titulo = titulo.substring(0, 40).trim() + "...";
        }
        conversa.setTitulo(titulo);
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
        List<ConversaResponse> lista = new ArrayList<>();
        if (conversas.isEmpty()) {
            return lista;
        }
        List<Integer> ids = new ArrayList<>();
        for (ConversaIa c : conversas) {
            ids.add(c.getId());
        }

        // Separa as mensagens por conversa
        Map<Integer, List<MensagemResponse>> mensagens = new HashMap<>();
        for (MensagemIa m : mensagemRepository.dasConversas(ids)) {
            Integer idConversa = m.getConversa().getId();
            if (mensagens.get(idConversa) == null) {
                mensagens.put(idConversa, new ArrayList<>());
            }
            String remetente = "user";
            if ("IA".equals(m.getPapel())) {
                remetente = "ia";
            }
            mensagens.get(idConversa).add(new MensagemResponse(m.getId(), remetente, m.getTexto(), m.getDataEnvio()));
        }

        for (ConversaIa c : conversas) {
            List<MensagemResponse> daConversa = mensagens.get(c.getId());
            if (daConversa == null) {
                daConversa = new ArrayList<>();
            }
            lista.add(new ConversaResponse(c.getId(), c.getTitulo(), c.getDataCriacao(), daConversa));
        }
        return lista;
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
        String chave = ip;
        if (chave == null) {
            chave = "?";
        }
        perguntasDemo.putIfAbsent(chave, new ArrayDeque<>());
        Deque<Long> fila = perguntasDemo.get(chave);
        if (fila == null) {
            fila = new ArrayDeque<>();
        }
        synchronized (fila) {
            while (!fila.isEmpty() && agora - fila.peekFirst() > DEMO_JANELA_MS) {
                fila.pollFirst();
            }
            if (fila.size() >= DEMO_LIMITE) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Limite de perguntas da demonstração atingido. Tente de novo em alguns minutos ou entre com sua conta.");
            }
            fila.addLast(agora);
        }
        // Limpa o mapa para não crescer sem limite
        if (perguntasDemo.size() > 10000) {
            perguntasDemo.clear();
        }
    }

    private static String texto(String valor) {
        String limpo = "";
        if (valor != null) {
            limpo = valor.trim();
        }
        if (limpo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escreva sua pergunta.");
        }
        if (limpo.length() > TEXTO_MAXIMO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pergunta muito longa (máximo 4000 caracteres).");
        }
        return limpo;
    }

    private static String recurso(String caminho) throws IOException {
        return new ClassPathResource(caminho).getContentAsString(StandardCharsets.UTF_8).trim();
    }
}
