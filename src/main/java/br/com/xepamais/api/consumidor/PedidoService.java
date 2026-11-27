package br.com.xepamais.api.consumidor;

import br.com.xepamais.api.consumidor.dto.ConsumidorDtos.*;
import br.com.xepamais.api.database.model.*;
import br.com.xepamais.api.database.repository.*;
import br.com.xepamais.api.plano.PlanoService;
import br.com.xepamais.api.xepa.Categorias;
import br.com.xepamais.api.xepa.XepaService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

// Checkout, histórico de pedidos e impacto do consumidor
@Service
public class PedidoService {
    static final String ORIGEM_XEPA = "Venda de xepa pelo Xepa+";
    static final String PENDENTE_RETIRADA = "Pendente de retirada";
    static final String RETIRADO = "Retirado";
    private static final BigDecimal TAXA_ENTREGA_MAXIMA = new BigDecimal("100.00");
    private static final BigDecimal CEM = new BigDecimal("100");

    // Peso médio da xepa por categoria, em kg
    private static final Map<String, BigDecimal> PESO_POR_CATEGORIA = Map.of(
            "Verduras e legumes", new BigDecimal("2"),
            "Frutas", new BigDecimal("2"),
            "Tempero", new BigDecimal("0.3"),
            "Completa", new BigDecimal("3"));
    private static final BigDecimal PESO_PADRAO = new BigDecimal("2");
    private static final BigDecimal KG_POR_REFEICAO = new BigDecimal("0.5");
    private static final int SEMANAS = 6;

    private final SecureRandom random = new SecureRandom();
    private final ConsumidorService consumidorService;
    private final XepaRepository xepaRepository;
    private final PedidoRepository pedidoRepository;
    private final ItemPedidoRepository itemPedidoRepository;
    private final ItemCarrinhoRepository itemCarrinhoRepository;
    private final PagamentoRepository pagamentoRepository;
    private final TipoPagamentoRepository tipoPagamentoRepository;
    private final RendaRepository rendaRepository;
    private final OrigemRendaRepository origemRendaRepository;
    private final FeiranteRepository feiranteRepository;
    private final PlanoService planoService;
    private final ChatRepository chatRepository;
    private final AvaliacaoRepository avaliacaoRepository;

    public PedidoService(ConsumidorService consumidorService, XepaRepository xepaRepository,
                         PedidoRepository pedidoRepository, ItemPedidoRepository itemPedidoRepository,
                         ItemCarrinhoRepository itemCarrinhoRepository, PagamentoRepository pagamentoRepository,
                         TipoPagamentoRepository tipoPagamentoRepository, RendaRepository rendaRepository,
                         OrigemRendaRepository origemRendaRepository, FeiranteRepository feiranteRepository,
                         ChatRepository chatRepository, PlanoService planoService,
                         AvaliacaoRepository avaliacaoRepository) {
        this.consumidorService = consumidorService;
        this.xepaRepository = xepaRepository;
        this.pedidoRepository = pedidoRepository;
        this.itemPedidoRepository = itemPedidoRepository;
        this.itemCarrinhoRepository = itemCarrinhoRepository;
        this.pagamentoRepository = pagamentoRepository;
        this.tipoPagamentoRepository = tipoPagamentoRepository;
        this.rendaRepository = rendaRepository;
        this.origemRendaRepository = origemRendaRepository;
        this.feiranteRepository = feiranteRepository;
        this.chatRepository = chatRepository;
        this.avaliacaoRepository = avaliacaoRepository;
        this.planoService = planoService;
    }

    // Checkout

    // "Já fiz o pagamento"
    @Transactional
    public List<PedidoResponse> finalizarCompra(Integer idConta, CheckoutRequest request) {
        Consumidor consumidor = consumidorService.consumidor(idConta);
        Carrinho carrinho = consumidorService.carrinhoDo(consumidor);

        Map<Integer, BigDecimal> pedidos;
        if (request == null || request.itens() == null || request.itens().isEmpty()) {
            pedidos = consumidorService.itensSalvos(carrinho);
        } else {
            pedidos = consumidorService.agrupar(request.itens());
        }
        if (pedidos.isEmpty()) {
            throw ConsumidorService.invalido("Seu carrinho está vazio.");
        }

        RecebimentoRequest recebimento = null;
        if (request != null) {
            recebimento = request.recebimento();
        }
        boolean entrega = false;
        if (recebimento != null && "entrega".equalsIgnoreCase(recebimento.tipo())) {
            entrega = true;
        }

        String endereco = null;
        Map<String, BigDecimal> fretes = new HashMap<>();
        if (entrega) {
            endereco = endereco(recebimento.endereco());
            consumidor.setRecebimentoPreferido("ENTREGA");
            // Salva o endereço para a próxima compra
            consumidorService.guardarEndereco(consumidor, recebimento.endereco());
            fretes = fretes(recebimento.fretes());
        } else {
            consumidor.setRecebimentoPreferido("RETIRADA");
        }

        String metodo = null;
        if (request != null) {
            metodo = request.metodoPagamento();
        }
        TipoPagamento tipoPagamento = tipoPagamento(metodo);

        // Trava as xepas e confere o estoque antes de gravar
        Map<Integer, Xepa> xepas = new HashMap<>();
        for (Xepa x : xepaRepository.travar(pedidos.keySet())) {
            xepas.put(x.getId(), x);
        }
        for (Integer idXepa : pedidos.keySet()) {
            Xepa xepa = xepas.get(idXepa);
            BigDecimal qtd = pedidos.get(idXepa);
            if (xepa == null || !XepaService.disponivel(xepa)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Uma das xepas do carrinho não está mais disponível. Atualize o carrinho.");
            }
            if (qtd.compareTo(xepa.getQuantidade()) > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Só restam "
                        + xepa.getQuantidade().stripTrailingZeros().toPlainString() + " de \""
                        + xepa.getProduto().getNome() + "\".");
            }
        }

        // Separa os itens por feirante
        Map<Feirante, List<Integer>> porFeirante = new LinkedHashMap<>();
        for (Integer idXepa : pedidos.keySet()) {
            Feirante dono = xepas.get(idXepa).getProduto().getFeirante();
            if (porFeirante.get(dono) == null) {
                porFeirante.put(dono, new ArrayList<>());
            }
            porFeirante.get(dono).add(idXepa);
        }

        List<Pedido> criados = new ArrayList<>();
        for (Feirante feirante : porFeirante.keySet()) {
            List<Integer> idsXepas = porFeirante.get(feirante);

            BigDecimal subtotal = BigDecimal.ZERO;
            for (Integer idXepa : idsXepas) {
                subtotal = subtotal.add(xepas.get(idXepa).getPreco().multiply(pedidos.get(idXepa)));
            }
            subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);

            // Frete: soma o frete de cada feira desse feirante (cada feira só uma vez)
            BigDecimal taxaEntrega = BigDecimal.ZERO;
            List<String> feirasSomadas = new ArrayList<>();
            for (Integer idXepa : idsXepas) {
                String nomeFeira = xepas.get(idXepa).getFeira().getNome();
                if (!feirasSomadas.contains(nomeFeira)) {
                    feirasSomadas.add(nomeFeira);
                    BigDecimal frete = fretes.get(nomeFeira.toLowerCase(Locale.ROOT));
                    if (frete != null) {
                        taxaEntrega = taxaEntrega.add(frete);
                    }
                }
            }

            // Comissão do plano do feirante
            BigDecimal percentual = planoService.comissaoDo(feirante);
            BigDecimal comissao = subtotal.multiply(percentual).divide(CEM, 2, RoundingMode.HALF_UP);

            Pedido pedido = new Pedido();
            pedido.setConsumidor(consumidor);
            pedido.setFeirante(feirante);
            pedido.setStatus("PAGO");
            pedido.setDataPedido(LocalDateTime.now(XepaService.FUSO));
            pedido.setValorTotal(subtotal.add(taxaEntrega));
            pedido.setPercentualComissao(percentual);
            pedido.setValorComissao(comissao);
            pedido.setCodigoRetirada(String.valueOf(1000 + random.nextInt(9000)));
            if (entrega) {
                pedido.setTipoRecebimento("ENTREGA");
            } else {
                pedido.setTipoRecebimento("RETIRADA");
            }
            pedido.setEnderecoEntrega(endereco);
            pedido.setTaxaEntrega(taxaEntrega);
            pedidoRepository.save(pedido);

            List<String> descricao = new ArrayList<>();
            for (Integer idXepa : idsXepas) {
                Xepa xepa = xepas.get(idXepa);
                BigDecimal qtd = pedidos.get(idXepa);
                ItemPedido item = new ItemPedido();
                item.setPedido(pedido);
                item.setXepa(xepa);
                item.setQuantidade(qtd);
                item.setPrecoUnitario(xepa.getPreco());
                itemPedidoRepository.save(item);

                xepa.setQuantidade(xepa.getQuantidade().subtract(qtd));
                if (xepa.getQuantidade().signum() <= 0) {
                    xepa.setStatus("ESGOTADA");
                }
                descricao.add(qtd.stripTrailingZeros().toPlainString() + "x " + xepa.getProduto().getNome());
            }

            Pagamento pagamento = new Pagamento();
            pagamento.setPedido(pedido);
            pagamento.setTipoPagamento(tipoPagamento);
            pagamento.setValor(pedido.getValorTotal());
            pagamento.setStatus("APROVADO");
            pagamento.setDataPagamento(LocalDateTime.now(XepaService.FUSO));
            pagamentoRepository.save(pagamento);

            Feira primeiraFeira = xepas.get(idsXepas.get(0)).getFeira();
            lancarNoFinanceiro(pedido, descricao, primeiraFeira);

            // O feirante recebe o pedido no chat na hora
            Chat chat = new Chat();
            chat.setConsumidor(consumidor);
            chat.setFeirante(feirante);
            chat.setPedido(pedido);
            chat.setDataInicio(pedido.getDataPedido());
            chat.setLeituraConsumidor(pedido.getDataPedido());
            chatRepository.save(chat);
            criados.add(pedido);
        }

        itemCarrinhoRepository.removerXepas(carrinho.getId(), pedidos.keySet());
        return respostas(criados);
    }

    // A venda entra como renda do feirante, sem a comissão do Xepa+
    private void lancarNoFinanceiro(Pedido pedido, List<String> itens, Feira feira) {
        BigDecimal liquido = pedido.getValorTotal().subtract(pedido.getValorComissao());
        if (liquido.signum() <= 0) {
            return;
        }
        OrigemRenda origem = origemRendaRepository.findByNomeIgnoreCase(ORIGEM_XEPA).orElse(null);
        if (origem == null) {
            origem = new OrigemRenda();
            origem.setNome(ORIGEM_XEPA);
            origem = origemRendaRepository.save(origem);
        }
        String descricao = "Pedido nº " + numero(pedido) + " - " + String.join(", ", itens);
        if (descricao.length() > 150) {
            descricao = descricao.substring(0, 147) + "...";
        }

        Renda renda = new Renda();
        renda.setFeirante(pedido.getFeirante());
        renda.setOrigem(origem);
        renda.setPedido(pedido);
        renda.setFeira(feira);
        renda.setDescricao(descricao);
        renda.setValor(liquido);
        renda.setData(XepaService.hoje());
        rendaRepository.save(renda);
    }

    // Consultas do consumidor

    @Transactional(readOnly = true)
    public List<PedidoResponse> listar(Integer idConta) {
        Consumidor consumidor = consumidorService.consumidor(idConta);
        return respostas(pedidoRepository.findByConsumidor_IdOrderByDataPedidoDescIdDesc(consumidor.getId()));
    }

    @Transactional(readOnly = true)
    public PedidoResponse detalhe(Integer idConta, Integer idPedido) {
        Consumidor consumidor = consumidorService.consumidor(idConta);
        Pedido pedido = pedidoDoConsumidor(idPedido, consumidor);
        return resposta(pedido);
    }

    // Impacto calculado a partir dos pedidos
    @Transactional(readOnly = true)
    public ImpactoResponse impacto(Integer idConta) {
        Consumidor consumidor = consumidorService.consumidor(idConta);
        List<Pedido> pedidos = new ArrayList<>();
        for (Pedido p : pedidoRepository.findByConsumidor_IdOrderByDataPedidoDescIdDesc(consumidor.getId())) {
            if (!"CANCELADO".equals(p.getStatus())) {
                pedidos.add(p);
            }
        }
        Map<Integer, List<ItemPedido>> itens = itensPorPedido(pedidos);

        LocalDate hoje = XepaService.hoje();
        LocalDate inicioPrimeiraSemana = hoje.minusDays(SEMANAS * 7L - 1);
        BigDecimal[] kgSemana = new BigDecimal[SEMANAS];
        for (int i = 0; i < SEMANAS; i++) {
            kgSemana[i] = BigDecimal.ZERO;
        }

        BigDecimal kg = BigDecimal.ZERO;
        BigDecimal economia = BigDecimal.ZERO;
        BigDecimal quantidade = BigDecimal.ZERO;
        for (Pedido pedido : pedidos) {
            BigDecimal kgPedido = BigDecimal.ZERO;
            List<ItemPedido> itensDoPedido = itens.get(pedido.getId());
            if (itensDoPedido == null) {
                itensDoPedido = new ArrayList<>();
            }
            for (ItemPedido item : itensDoPedido) {
                Produto produto = item.getXepa().getProduto();
                String categoria = Categorias.paraApp(produto.getCategoria().getNome());
                BigDecimal peso = PESO_POR_CATEGORIA.get(categoria);
                if (peso == null) {
                    peso = PESO_PADRAO;
                }
                kgPedido = kgPedido.add(peso.multiply(item.getQuantidade()));
                quantidade = quantidade.add(item.getQuantidade());

                // Economia: diferença para o preço normal (sem preço normal, conta o preço pago)
                BigDecimal referencia = produto.getPrecoReferencia();
                BigDecimal porUnidade;
                if (referencia != null && referencia.compareTo(item.getPrecoUnitario()) > 0) {
                    porUnidade = referencia.subtract(item.getPrecoUnitario());
                } else {
                    porUnidade = item.getPrecoUnitario();
                }
                economia = economia.add(porUnidade.multiply(item.getQuantidade()));
            }
            kg = kg.add(kgPedido);

            if (pedido.getDataPedido() != null) {
                long dias = ChronoUnit.DAYS.between(inicioPrimeiraSemana, pedido.getDataPedido().toLocalDate());
                if (dias >= 0 && dias < SEMANAS * 7L) {
                    int semana = (int) (dias / 7);
                    kgSemana[semana] = kgSemana[semana].add(kgPedido);
                }
            }
        }

        List<SemanaImpacto> semanas = new ArrayList<>();
        for (int i = 0; i < SEMANAS; i++) {
            semanas.add(new SemanaImpacto(i + 1, "Sem " + (i + 1), inicioPrimeiraSemana.plusWeeks(i),
                    kgSemana[i].setScale(1, RoundingMode.HALF_UP)));
        }
        int refeicoes = kg.divide(KG_POR_REFEICAO, 0, RoundingMode.HALF_UP).intValue();
        return new ImpactoResponse(kg.setScale(1, RoundingMode.HALF_UP), economia.setScale(2, RoundingMode.HALF_UP),
                quantidade.stripTrailingZeros(), refeicoes, pedidos.size(), semanas);
    }

    // Lado do feirante

    @Transactional(readOnly = true)
    public List<PedidoResponse> vendasDoFeirante(Integer idConta) {
        Feirante feirante = feirante(idConta);
        List<PedidoResponse> lista = new ArrayList<>();
        for (PedidoResponse r : respostas(pedidoRepository.findByFeirante_IdOrderByIdDesc(feirante.getId()))) {
            lista.add(r.semCodigo());
        }
        return lista;
    }

    // O feirante confirma a retirada/entrega com o código que o consumidor mostra
    @Transactional
    public PedidoResponse confirmarRetirada(Integer idConta, Integer idPedido, RetiradaRequest request) {
        Feirante feirante = feirante(idConta);
        Pedido pedido = pedidoDoFeirante(idPedido, feirante);
        if ("CANCELADO".equals(pedido.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este pedido foi cancelado.");
        }
        String codigo = "";
        if (request != null && request.codigo() != null) {
            codigo = request.codigo().trim();
        }
        if (!codigo.equals(pedido.getCodigoRetirada())) {
            throw ConsumidorService.invalido("Código de retirada incorreto.");
        }
        marcarRetirado(pedido);
        return resposta(pedido).semCodigo();
    }

    // Cancelamento

    // O consumidor cancela antes de retirar
    @Transactional
    public PedidoResponse cancelarPeloConsumidor(Integer idConta, Integer idPedido) {
        Consumidor consumidor = consumidorService.consumidor(idConta);
        Pedido pedido = pedidoDoConsumidor(idPedido, consumidor);
        cancelar(pedido);
        return resposta(pedido);
    }

    // O feirante cancela
    @Transactional
    public PedidoResponse cancelarPeloFeirante(Integer idConta, Integer idPedido) {
        Feirante feirante = feirante(idConta);
        Pedido pedido = pedidoDoFeirante(idPedido, feirante);
        cancelar(pedido);
        return resposta(pedido).semCodigo();
    }

    private void cancelar(Pedido pedido) {
        if ("RETIRADO".equals(pedido.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pedido já retirado não pode ser cancelado.");
        }
        if ("CANCELADO".equals(pedido.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este pedido já foi cancelado.");
        }

        // Devolve as quantidades para as xepas
        List<ItemPedido> itens = itemPedidoRepository.dosPedidos(List.of(pedido.getId()));
        List<Integer> idsXepas = new ArrayList<>();
        for (ItemPedido item : itens) {
            idsXepas.add(item.getXepa().getId());
        }
        Map<Integer, Xepa> xepas = new HashMap<>();
        for (Xepa x : xepaRepository.travar(idsXepas)) {
            xepas.put(x.getId(), x);
        }
        for (ItemPedido item : itens) {
            Xepa xepa = xepas.get(item.getXepa().getId());
            if (xepa == null) {
                continue;
            }
            xepa.setQuantidade(xepa.getQuantidade().add(item.getQuantidade()));
            if ("ESGOTADA".equals(xepa.getStatus())) {
                xepa.setStatus("DISPONIVEL");
            }
        }

        for (Pagamento pagamento : pagamentoRepository.findByPedido_Id(pedido.getId())) {
            if ("APROVADO".equals(pagamento.getStatus())) {
                pagamento.setStatus("ESTORNADO");
            } else if ("PENDENTE".equals(pagamento.getStatus())) {
                pagamento.setStatus("RECUSADO");
            }
        }

        Renda renda = rendaRepository.findByPedido_Id(pedido.getId()).orElse(null);
        if (renda != null) {
            rendaRepository.delete(renda);
        }
        finalizarChat(pedido);
        pedido.setStatus("CANCELADO");
    }

    // Retirado/entregue: o pedido muda de status e a conversa vai para "Finalizadas"
    public void marcarRetirado(Pedido pedido) {
        if (!"CANCELADO".equals(pedido.getStatus())) {
            pedido.setStatus("RETIRADO");
        }
        finalizarChat(pedido);
    }

    private void finalizarChat(Pedido pedido) {
        Chat chat = chatRepository.findByPedido_Id(pedido.getId()).orElse(null);
        if (chat != null && !"FINALIZADA".equals(chat.getStatus())) {
            chat.setStatus("FINALIZADA");
            chat.setDataFinalizacao(LocalDateTime.now(XepaService.FUSO));
        }
    }

    // Auxiliares

    private Feirante feirante(Integer idConta) {
        Feirante feirante = feiranteRepository.findByConta_Id(idConta).orElse(null);
        if (feirante == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Feirante não encontrado.");
        }
        return feirante;
    }

    private Pedido pedidoDoConsumidor(Integer idPedido, Consumidor consumidor) {
        Pedido pedido = pedidoRepository.findByIdAndConsumidor_Id(idPedido, consumidor.getId()).orElse(null);
        if (pedido == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado.");
        }
        return pedido;
    }

    private Pedido pedidoDoFeirante(Integer idPedido, Feirante feirante) {
        Pedido pedido = pedidoRepository.findById(idPedido).orElse(null);
        if (pedido == null || !pedido.getFeirante().getId().equals(feirante.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado.");
        }
        return pedido;
    }

    private Map<Integer, List<ItemPedido>> itensPorPedido(List<Pedido> pedidos) {
        Map<Integer, List<ItemPedido>> mapa = new HashMap<>();
        if (pedidos.isEmpty()) {
            return mapa;
        }
        List<Integer> ids = new ArrayList<>();
        for (Pedido p : pedidos) {
            ids.add(p.getId());
        }
        for (ItemPedido item : itemPedidoRepository.dosPedidos(ids)) {
            Integer idPedido = item.getPedido().getId();
            if (mapa.get(idPedido) == null) {
                mapa.put(idPedido, new ArrayList<>());
            }
            mapa.get(idPedido).add(item);
        }
        return mapa;
    }

    private PedidoResponse resposta(Pedido pedido) {
        List<Pedido> lista = new ArrayList<>();
        lista.add(pedido);
        return respostas(lista).get(0);
    }

    public List<PedidoResponse> respostas(List<Pedido> pedidos) {
        List<PedidoResponse> lista = new ArrayList<>();
        if (pedidos.isEmpty()) {
            return lista;
        }
        Map<Integer, List<ItemPedido>> itens = itensPorPedido(pedidos);
        List<Integer> ids = new ArrayList<>();
        for (Pedido p : pedidos) {
            ids.add(p.getId());
        }

        Map<Integer, Integer> conversas = new HashMap<>();
        for (Chat c : chatRepository.findByPedido_IdIn(ids)) {
            if (!conversas.containsKey(c.getPedido().getId())) {
                conversas.put(c.getPedido().getId(), c.getId());
            }
        }

        Map<Integer, AvaliacaoResponse> avaliacoes = new HashMap<>();
        for (Avaliacao a : avaliacaoRepository.findByPedido_IdIn(ids)) {
            if (!avaliacoes.containsKey(a.getPedido().getId())) {
                avaliacoes.put(a.getPedido().getId(),
                        new AvaliacaoResponse(a.getNota(), a.getComentario(), a.getDataAvaliacao()));
            }
        }

        for (Pedido p : pedidos) {
            List<ItemPedido> itensDoPedido = itens.get(p.getId());
            if (itensDoPedido == null) {
                itensDoPedido = new ArrayList<>();
            }
            lista.add(montarResposta(p, itensDoPedido, conversas.get(p.getId()), avaliacoes.get(p.getId())));
        }
        return lista;
    }

    private static PedidoResponse montarResposta(Pedido pedido, List<ItemPedido> itens, Integer idConversa,
                                                 AvaliacaoResponse avaliacao) {
        Feirante feirante = pedido.getFeirante();
        List<ItemPedidoResponse> itensResposta = new ArrayList<>();
        for (ItemPedido item : itens) {
            Xepa xepa = item.getXepa();
            Produto produto = xepa.getProduto();
            itensResposta.add(new ItemPedidoResponse(xepa.getId(), feirante.getConta().getId(), produto.getNome(),
                    Categorias.paraApp(produto.getCategoria().getNome()), produto.getDescricao(), produto.getImagem(),
                    feirante.getNomeFantasia(), xepa.getFeira().getNome(), XepaService.localDaFeira(xepa.getFeira()),
                    item.getQuantidade(), item.getPrecoUnitario()));
        }

        BigDecimal taxa = pedido.getTaxaEntrega();
        if (taxa == null) {
            taxa = BigDecimal.ZERO;
        }
        RecebimentoResponse recebimento;
        if ("ENTREGA".equals(pedido.getTipoRecebimento())) {
            recebimento = new RecebimentoResponse("entrega", pedido.getEnderecoEntrega(), new FreteResponse(taxa));
        } else {
            recebimento = new RecebimentoResponse("retirada", pedido.getEnderecoEntrega(), null);
        }

        return new PedidoResponse(pedido.getId(), numero(pedido), pedido.getDataPedido(), statusDoApp(pedido.getStatus()),
                pedido.getStatus(), pedido.getCodigoRetirada(), pedido.getValorTotal().subtract(taxa), taxa,
                pedido.getValorTotal(), feirante.getConta().getId(), feirante.getNomeFantasia(),
                pedido.getConsumidor().getConta().getNome(), recebimento, itensResposta, idConversa, avaliacao);
    }

    static String numero(Pedido pedido) {
        return String.format("%04d", pedido.getId());
    }

    private static String statusDoApp(String status) {
        if (status.equals("RETIRADO")) {
            return RETIRADO;
        } else if (status.equals("CANCELADO")) {
            return "Cancelado";
        } else if (status.equals("AGUARDANDO_PAGAMENTO")) {
            return "Aguardando pagamento";
        } else {
            return PENDENTE_RETIRADA;
        }
    }

    private TipoPagamento tipoPagamento(String metodo) {
        String nome = "PIX";
        if (metodo != null && !metodo.isBlank()) {
            nome = metodo.trim().toUpperCase(Locale.ROOT);
        }
        TipoPagamento tipo = tipoPagamentoRepository.findByMetodoPagamentoIgnoreCase(nome).orElse(null);
        if (tipo != null) {
            return tipo;
        }
        if (!nome.equals("PIX") && !nome.equals("CARTAO_CREDITO") && !nome.equals("CARTAO_DEBITO")
                && !nome.equals("DINHEIRO")) {
            throw ConsumidorService.invalido("Forma de pagamento inválida.");
        }
        TipoPagamento novo = new TipoPagamento();
        novo.setMetodoPagamento(nome);
        return tipoPagamentoRepository.save(novo);
    }

    private static String endereco(EnderecoRequest e) {
        if (e == null || vazio(e.logradouro()) || vazio(e.numero()) || vazio(e.bairro())
                || vazio(e.cidade()) || vazio(e.uf()) || e.cep() == null
                || e.cep().replaceAll("\\D", "").length() != 8) {
            throw ConsumidorService.invalido("Preencha o endereço completo para a entrega.");
        }
        String texto = e.logradouro().trim() + ", " + e.numero().trim();
        if (!vazio(e.complemento())) {
            texto = texto + " - " + e.complemento().trim();
        }
        texto = texto + " - " + e.bairro().trim() + ", " + e.cidade().trim() + "/" + e.uf().trim().toUpperCase(Locale.ROOT);
        texto = texto + " - CEP " + e.cep().replaceAll("\\D", "");
        if (texto.length() > 255) {
            throw ConsumidorService.invalido("Endereço muito longo.");
        }
        return texto;
    }

    private static Map<String, BigDecimal> fretes(List<FreteFeiraRequest> fretes) {
        Map<String, BigDecimal> mapa = new HashMap<>();
        if (fretes == null) {
            return mapa;
        }
        for (FreteFeiraRequest frete : fretes) {
            if (frete == null || vazio(frete.feira()) || frete.valor() == null) {
                continue;
            }
            if (frete.valor().signum() < 0 || frete.valor().compareTo(TAXA_ENTREGA_MAXIMA) > 0) {
                throw ConsumidorService.invalido("Taxa de entrega inválida.");
            }
            mapa.put(frete.feira().trim().toLowerCase(Locale.ROOT), frete.valor().setScale(2, RoundingMode.HALF_UP));
        }
        return mapa;
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }
}
