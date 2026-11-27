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

// Chat entre consumidor e feirante, guardado no banco. papel: "consumidor" ou "feirante"
@Service
public class ConversaService {
    public static final String CONSUMIDOR = "consumidor";
    public static final String FEIRANTE = "feirante";
    private static final int TEXTO_MAXIMO = 2000;
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    public record LeiturasResponse(LocalDateTime feirante, LocalDateTime consumidor) {}

    public record XepaResumo(Integer id, String nome, BigDecimal preco, String feira) {}

    // autor: "sistema", "consumidor" ou "feirante"
    public record MensagemResponse(Integer id, String autor, String texto, LocalDateTime data) {}

    // status: "recente" ou "finalizada"
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
        List<Chat> chats;
        if (CONSUMIDOR.equals(papel)) {
            chats = chatRepository.findByConsumidor_IdOrderByIdDesc(consumidor(idConta).getId());
        } else {
            chats = chatRepository.findByFeirante_IdOrderByIdDesc(feirante(idConta).getId());
        }
        return respostas(chats, papel);
    }

    @Transactional(readOnly = true)
    public ConversaResponse buscar(String papel, Integer idConta, Integer idChat) {
        Chat chat = doParticipante(papel, idConta, idChat);
        return resposta(chat, papel);
    }

    // Botão "Falar com o feirante": usa a dúvida que já estiver aberta sobre essa xepa
    @Transactional
    public ConversaResponse abrirDuvida(Integer idConta, Integer idXepa) {
        Consumidor consumidor = consumidor(idConta);
        if (idXepa == null) {
            throw invalido("Informe a xepa.");
        }
        Chat chat = chatRepository.duvidaAberta(consumidor.getId(), idXepa).orElse(null);
        if (chat == null) {
            Xepa xepa = xepaRepository.findById(idXepa).orElse(null);
            if (xepa == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Xepa não encontrada.");
            }
            LocalDateTime agora = agora();
            chat = new Chat();
            chat.setConsumidor(consumidor);
            chat.setFeirante(xepa.getProduto().getFeirante());
            chat.setXepa(xepa);
            chat.setDataInicio(agora);
            chat.setLeituraConsumidor(agora);
            chat = chatRepository.save(chat);
        }
        return resposta(chat, CONSUMIDOR);
    }

    @Transactional
    public ConversaResponse enviar(String papel, Integer idConta, Integer idChat, String texto) {
        Chat chat = doParticipante(papel, idConta, idChat);
        if ("FINALIZADA".equals(chat.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta conversa foi finalizada.");
        }
        String limpo = "";
        if (texto != null) {
            limpo = texto.trim();
        }
        if (limpo.isEmpty()) {
            throw invalido("Escreva uma mensagem.");
        }
        if (limpo.length() > TEXTO_MAXIMO) {
            throw invalido("Mensagem muito longa (máximo 2000 caracteres).");
        }

        LocalDateTime agora = agora();
        Mensagem mensagem = new Mensagem();
        mensagem.setChat(chat);
        if (CONSUMIDOR.equals(papel)) {
            mensagem.setConta(chat.getConsumidor().getConta());
        } else {
            mensagem.setConta(chat.getFeirante().getConta());
        }
        mensagem.setTexto(limpo);
        mensagem.setDataEnvio(agora);
        mensagemRepository.save(mensagem);
        marcarLeitura(chat, papel, agora);
        return resposta(chat, papel);
    }

    @Transactional
    public ConversaResponse marcarComoLida(String papel, Integer idConta, Integer idChat) {
        Chat chat = doParticipante(papel, idConta, idChat);
        marcarLeitura(chat, papel, agora());
        return resposta(chat, papel);
    }

    // Botão "Pedido retirado" do feirante: finaliza a conversa e marca o pedido como retirado
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
        return resposta(chat, FEIRANTE);
    }

    // Auxiliares

    private void marcarLeitura(Chat chat, String papel, LocalDateTime quando) {
        if (CONSUMIDOR.equals(papel)) {
            chat.setLeituraConsumidor(quando);
        } else {
            chat.setLeituraFeirante(quando);
        }
    }

    private Chat doParticipante(String papel, Integer idConta, Integer idChat) {
        Chat chat = chatRepository.findById(idChat).orElse(null);
        if (chat == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversa não encontrada.");
        }
        Integer dono;
        if (CONSUMIDOR.equals(papel)) {
            dono = chat.getConsumidor().getConta().getId();
        } else {
            dono = chat.getFeirante().getConta().getId();
        }
        if (!dono.equals(idConta)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversa não encontrada.");
        }
        return chat;
    }

    private ConversaResponse resposta(Chat chat, String papel) {
        List<Chat> lista = new ArrayList<>();
        lista.add(chat);
        return respostas(lista, papel).get(0);
    }

    private List<ConversaResponse> respostas(List<Chat> chats, String papel) {
        List<ConversaResponse> respostas = new ArrayList<>();
        if (chats.isEmpty()) {
            return respostas;
        }

        // Busca as mensagens de todos os chats de uma vez e separa por chat
        List<Integer> idsChats = new ArrayList<>();
        List<Pedido> pedidos = new ArrayList<>();
        for (Chat chat : chats) {
            idsChats.add(chat.getId());
            if (chat.getPedido() != null) {
                pedidos.add(chat.getPedido());
            }
        }
        Map<Integer, List<Mensagem>> mensagens = new HashMap<>();
        for (Mensagem m : mensagemRepository.dosChats(idsChats)) {
            Integer idChat = m.getChat().getId();
            if (mensagens.get(idChat) == null) {
                mensagens.put(idChat, new ArrayList<>());
            }
            mensagens.get(idChat).add(m);
        }
        Map<Integer, PedidoResponse> pedidosResposta = new HashMap<>();
        for (PedidoResponse p : pedidoService.respostas(pedidos)) {
            pedidosResposta.put(p.id(), p);
        }

        for (Chat chat : chats) {
            PedidoResponse pedido = null;
            if (chat.getPedido() != null) {
                pedido = pedidosResposta.get(chat.getPedido().getId());
            }
            if (pedido != null && FEIRANTE.equals(papel)) {
                pedido = pedido.semCodigo();
            }
            Xepa xepa = chat.getXepa();
            XepaResumo resumo = null;
            if (xepa != null) {
                resumo = new XepaResumo(xepa.getId(), xepa.getProduto().getNome(), xepa.getPreco(), xepa.getFeira().getNome());
            }

            // Primeira mensagem é um aviso do sistema
            String aviso;
            if (pedido != null) {
                aviso = "Novo pedido nº " + pedido.numero() + " realizado. Total: "
                        + NumberFormat.getCurrencyInstance(PT_BR).format(pedido.total()) + ".";
            } else if (resumo != null) {
                aviso = "Conversa sobre a " + resumo.nome() + ".";
            } else {
                aviso = "Conversa sobre a xepa.";
            }
            List<MensagemResponse> lista = new ArrayList<>();
            lista.add(new MensagemResponse(0, "sistema", aviso, chat.getDataInicio()));

            Integer contaConsumidor = chat.getConsumidor().getConta().getId();
            List<Mensagem> doChat = mensagens.get(chat.getId());
            if (doChat == null) {
                doChat = new ArrayList<>();
            }
            for (Mensagem m : doChat) {
                String autor = FEIRANTE;
                if (m.getConta().getId().equals(contaConsumidor)) {
                    autor = CONSUMIDOR;
                }
                lista.add(new MensagemResponse(m.getId(), autor, m.getTexto(), m.getDataEnvio()));
            }

            String status = "recente";
            if ("FINALIZADA".equals(chat.getStatus())) {
                status = "finalizada";
            }
            respostas.add(new ConversaResponse(chat.getId(), chat.getConsumidor().getConta().getNome(),
                    chat.getFeirante().getConta().getId(), chat.getFeirante().getNomeFantasia(), status,
                    chat.getDataInicio(), chat.getDataFinalizacao(),
                    new LeiturasResponse(chat.getLeituraFeirante(), chat.getLeituraConsumidor()),
                    pedido, resumo, lista));
        }
        return respostas;
    }

    private Consumidor consumidor(Integer idConta) {
        Consumidor consumidor = consumidorRepository.findByConta_Id(idConta).orElse(null);
        if (consumidor == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Consumidor não encontrado.");
        }
        return consumidor;
    }

    private Feirante feirante(Integer idConta) {
        Feirante feirante = feiranteRepository.findByConta_Id(idConta).orElse(null);
        if (feirante == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Feirante não encontrado.");
        }
        return feirante;
    }

    private static LocalDateTime agora() {
        return LocalDateTime.now(XepaService.FUSO);
    }

    private static ResponseStatusException invalido(String mensagem) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
    }
}
