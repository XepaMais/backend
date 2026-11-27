package br.com.xepamais.api.chat;

import br.com.xepamais.api.consumidor.PedidoService;
import br.com.xepamais.api.consumidor.dto.ConsumidorDtos.PedidoResponse;
import br.com.xepamais.api.database.model.*;
import br.com.xepamais.api.database.repository.*;
import br.com.xepamais.api.xepa.XepaService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

// Chat entre consumidor e feirante, guardado no banco (vale em qualquer aparelho).
// papel: "consumidor" ou "feirante" — o mesmo nome que o front usa como autor das mensagens.
@Service
public class ConversaService {
    public static final String CONSUMIDOR = "consumidor";
    public static final String FEIRANTE = "feirante";
    private static final int TEXTO_MAXIMO = 2000;
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    public record LeiturasResponse(LocalDateTime feirante, LocalDateTime consumidor) {}

    public record XepaResumo(Integer id, String nome, BigDecimal preco, String feira) {}

    // autor: "sistema", "consumidor" ou "feirante".
    public record MensagemResponse(Integer id, String autor, String texto, LocalDateTime data) {}

    // Mesmo formato das conversas do front. status: "recente" ou "finalizada".
    public record ConversaResponse(Integer id, String consumidor, Integer idFeirante, String feirante, String status,
                                   LocalDateTime criadaEm, LocalDateTime finalizadaEm, LeiturasResponse leituras,
                                   PedidoResponse pedido, XepaResumo xepa, List<MensagemResponse> mensagens) {}

    private final ChatRepository chatRepository;
    private final MensagemRepository mensagemRepository;
    private final ConsumidorRepository consumidorRepository;
    private final FeiranteRepository feiranteRepository;
    private final XepaRepository xepaRepository;
    private final PedidoService pedidoService;

    public ConversaService(ChatRepository chatRepository, MensagemRepository mensagemRepository,
                           ConsumidorRepository consumidorRepository, FeiranteRepository feiranteRepository,
                           XepaRepository xepaRepository, PedidoService pedidoService) {
        this.chatRepository = chatRepository;
        this.mensagemRepository = mensagemRepository;
        this.consumidorRepository = consumidorRepository;
        this.feiranteRepository = feiranteRepository;
        this.xepaRepository = xepaRepository;
        this.pedidoService = pedidoService;
    }

    @Transactional(readOnly = true)
    public List<ConversaResponse> listar(String papel, Integer idConta) {
        List<Chat> chats = CONSUMIDOR.equals(papel)
                ? chatRepository.findByConsumidor_IdOrderByIdDesc(consumidor(idConta).getId())
                : chatRepository.findByFeirante_IdOrderByIdDesc(feirante(idConta).getId());
        return respostas(chats, papel);
    }

    @Transactional(readOnly = true)
    public ConversaResponse buscar(String papel, Integer idConta, Integer idChat) {
        return respostas(List.of(doParticipante(papel, idConta, idChat)), papel).get(0);
    }

    // Botão "Falar com o feirante" na página da xepa: reaproveita a dúvida aberta sobre ela.
    @Transactional
    public ConversaResponse abrirDuvida(Integer idConta, Integer idXepa) {
        Consumidor consumidor = consumidor(idConta);
        if (idXepa == null) throw invalido("Informe a xepa.");
        Chat chat = chatRepository.duvidaAberta(consumidor.getId(), idXepa).orElseGet(() -> {
            Xepa xepa = xepaRepository.findById(idXepa)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Xepa não encontrada."));
            LocalDateTime agora = agora();
            Chat novo = new Chat();
            novo.setConsumidor(consumidor);
            novo.setFeirante(xepa.getProduto().getFeirante());
            novo.setXepa(xepa);
            novo.setDataInicio(agora);
            novo.setLeituraConsumidor(agora);
            return chatRepository.save(novo);
        });
        return respostas(List.of(chat), CONSUMIDOR).get(0);
    }

    @Transactional
    public ConversaResponse enviar(String papel, Integer idConta, Integer idChat, String texto) {
        Chat chat = doParticipante(papel, idConta, idChat);
        if ("FINALIZADA".equals(chat.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta conversa foi finalizada.");
        }
        String limpo = texto == null ? "" : texto.trim();
        if (limpo.isEmpty()) throw invalido("Escreva uma mensagem.");
        if (limpo.length() > TEXTO_MAXIMO) throw invalido("Mensagem muito longa (máximo 2000 caracteres).");

        LocalDateTime agora = agora();
        Mensagem mensagem = new Mensagem();
        mensagem.setChat(chat);
        mensagem.setConta(CONSUMIDOR.equals(papel) ? chat.getConsumidor().getConta() : chat.getFeirante().getConta());
        mensagem.setTexto(limpo);
        mensagem.setDataEnvio(agora);
        mensagemRepository.save(mensagem);
        marcarLeitura(chat, papel, agora);
        return respostas(List.of(chat), papel).get(0);
    }

    @Transactional
    public ConversaResponse marcarComoLida(String papel, Integer idConta, Integer idChat) {
        Chat chat = doParticipante(papel, idConta, idChat);
        marcarLeitura(chat, papel, agora());
        return respostas(List.of(chat), papel).get(0);
    }

    // Botão "Pedido retirado" do feirante: finaliza a conversa e marca o pedido como retirado.
    @Transactional
    public ConversaResponse finalizar(Integer idConta, Integer idChat) {
        Chat chat = doParticipante(FEIRANTE, idConta, idChat);
        if (chat.getPedido() != null) {
            pedidoService.marcarRetirado(chat.getPedido());
        }
        if (!"FINALIZADA".equals(chat.getStatus())) {
            chat.setStatus("FINALIZADA");
            chat.setDataFinalizacao(agora());
        }
        return respostas(List.of(chat), FEIRANTE).get(0);
    }

    // ---------- Auxiliares ----------

    private void marcarLeitura(Chat chat, String papel, LocalDateTime quando) {
        if (CONSUMIDOR.equals(papel)) chat.setLeituraConsumidor(quando);
        else chat.setLeituraFeirante(quando);
    }

    private Chat doParticipante(String papel, Integer idConta, Integer idChat) {
        Chat chat = chatRepository.findById(idChat)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversa não encontrada."));
        Integer dono = CONSUMIDOR.equals(papel)
                ? chat.getConsumidor().getConta().getId() : chat.getFeirante().getConta().getId();
        if (!dono.equals(idConta)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversa não encontrada.");
        }
        return chat;
    }

    private List<ConversaResponse> respostas(List<Chat> chats, String papel) {
        if (chats.isEmpty()) return List.of();
        Map<Integer, List<Mensagem>> mensagens = mensagemRepository.dosChats(chats.stream().map(Chat::getId).toList())
                .stream().collect(Collectors.groupingBy(m -> m.getChat().getId()));
        List<Pedido> pedidos = chats.stream().map(Chat::getPedido).filter(Objects::nonNull).toList();
        Map<Integer, PedidoResponse> pedidosResposta = pedidoService.respostas(pedidos).stream()
                .collect(Collectors.toMap(PedidoResponse::id, p -> p));

        List<ConversaResponse> respostas = new ArrayList<>();
        for (Chat chat : chats) {
            PedidoResponse pedido = chat.getPedido() == null ? null : pedidosResposta.get(chat.getPedido().getId());
            if (pedido != null && FEIRANTE.equals(papel)) pedido = pedido.semCodigo();
            Xepa xepa = chat.getXepa();
            XepaResumo resumo = xepa == null ? null : new XepaResumo(xepa.getId(), xepa.getProduto().getNome(),
                    xepa.getPreco(), xepa.getFeira().getNome());

            List<MensagemResponse> lista = new ArrayList<>();
            String aviso = pedido != null
                    ? "Novo pedido nº " + pedido.numero() + " realizado. Total: "
                      + NumberFormat.getCurrencyInstance(PT_BR).format(pedido.total()) + "."
                    : "Conversa sobre a " + (resumo == null ? "xepa" : resumo.nome()) + ".";
            lista.add(new MensagemResponse(0, "sistema", aviso, chat.getDataInicio()));
            Integer contaConsumidor = chat.getConsumidor().getConta().getId();
            for (Mensagem m : mensagens.getOrDefault(chat.getId(), List.of())) {
                lista.add(new MensagemResponse(m.getId(),
                        m.getConta().getId().equals(contaConsumidor) ? CONSUMIDOR : FEIRANTE,
                        m.getTexto(), m.getDataEnvio()));
            }

            respostas.add(new ConversaResponse(chat.getId(), chat.getConsumidor().getConta().getNome(),
                    chat.getFeirante().getConta().getId(), chat.getFeirante().getNomeFantasia(),
                    "FINALIZADA".equals(chat.getStatus()) ? "finalizada" : "recente",
                    chat.getDataInicio(), chat.getDataFinalizacao(),
                    new LeiturasResponse(chat.getLeituraFeirante(), chat.getLeituraConsumidor()),
                    pedido, resumo, lista));
        }
        return respostas;
    }

    private Consumidor consumidor(Integer idConta) {
        return consumidorRepository.findByConta_Id(idConta)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Consumidor não encontrado."));
    }

    private Feirante feirante(Integer idConta) {
        return feiranteRepository.findByConta_Id(idConta)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Feirante não encontrado."));
    }

    private static LocalDateTime agora() {
        return LocalDateTime.now(XepaService.FUSO);
    }

    private static ResponseStatusException invalido(String mensagem) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
    }
}
