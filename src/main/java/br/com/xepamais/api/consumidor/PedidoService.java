package br.com.xepamais.api.consumidor;

import br.com.xepamais.api.consumidor.dto.ConsumidorDtos.*;
import br.com.xepamais.api.database.model.*;
import br.com.xepamais.api.database.repository.*;
import br.com.xepamais.api.xepa.Categorias;
import br.com.xepamais.api.xepa.XepaService;
import jakarta.persistence.EntityManager;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

// Checkout, histórico de pedidos e impacto do consumidor; vendas e retirada do lado do feirante.
@Service
public class PedidoService {
    static final String ORIGEM_XEPA = "Venda de xepa pelo Xepa+";
    static final String PENDENTE_RETIRADA = "Pendente de retirada";
    static final String RETIRADO = "Retirado";
    private static final BigDecimal TAXA_ENTREGA_MAXIMA = new BigDecimal("100.00");
    private static final BigDecimal CEM = new BigDecimal("100");

    // Estimativas de impacto (as mesmas que o front usava): peso médio da xepa por categoria, em kg.
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
    private final EntityManager entityManager;

    public PedidoService(ConsumidorService consumidorService, XepaRepository xepaRepository,
                         PedidoRepository pedidoRepository, ItemPedidoRepository itemPedidoRepository,
                         ItemCarrinhoRepository itemCarrinhoRepository, PagamentoRepository pagamentoRepository,
                         TipoPagamentoRepository tipoPagamentoRepository, RendaRepository rendaRepository,
                         OrigemRendaRepository origemRendaRepository, FeiranteRepository feiranteRepository,
                         EntityManager entityManager) {
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
        this.entityManager = entityManager;
    }

    // ---------- Checkout ----------

    // "Já fiz o pagamento": cria UM pedido POR FEIRANTE, baixa o estoque, registra o pagamento,
    // lança a venda no financeiro do feirante e tira os itens comprados do carrinho.
    // Ainda não há gateway de pagamento: a confirmação do consumidor aprova o Pix.
    @Transactional
    public List<PedidoResponse> finalizarCompra(Integer idConta, CheckoutRequest request) {
        Consumidor consumidor = consumidorService.consumidor(idConta);
        Carrinho carrinho = consumidorService.carrinhoDo(consumidor);

        Map<Integer, BigDecimal> pedidos = request == null || request.itens() == null || request.itens().isEmpty()
                ? consumidorService.itensSalvos(carrinho)
                : consumidorService.agrupar(request.itens());
        if (pedidos.isEmpty()) throw ConsumidorService.invalido("Seu carrinho está vazio.");

        RecebimentoRequest recebimento = request == null ? null : request.recebimento();
        boolean entrega = recebimento != null && "entrega".equalsIgnoreCase(recebimento.tipo());
        String endereco = entrega ? endereco(recebimento.endereco()) : null;
        Map<String, BigDecimal> fretes = entrega ? fretes(recebimento.fretes()) : Map.of();
        TipoPagamento tipoPagamento = tipoPagamento(request == null ? null : request.metodoPagamento());

        // Trava as xepas e confere estoque antes de gravar qualquer coisa.
        Map<Integer, Xepa> xepas = xepaRepository.travar(pedidos.keySet()).stream()
                .collect(Collectors.toMap(Xepa::getId, x -> x));
        for (Map.Entry<Integer, BigDecimal> item : pedidos.entrySet()) {
            Xepa xepa = xepas.get(item.getKey());
            if (xepa == null || !XepaService.disponivel(xepa)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Uma das xepas do carrinho não está mais disponível. Atualize o carrinho.");
            }
            if (item.getValue().compareTo(xepa.getQuantidade()) > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Só restam "
                        + xepa.getQuantidade().stripTrailingZeros().toPlainString() + " de \""
                        + xepa.getProduto().getNome() + "\".");
            }
        }

        // Separa por feirante, mantendo a ordem do carrinho.
        Map<Feirante, List<Integer>> porFeirante = new LinkedHashMap<>();
        for (Integer idXepa : pedidos.keySet()) {
            porFeirante.computeIfAbsent(xepas.get(idXepa).getProduto().getFeirante(), f -> new ArrayList<>()).add(idXepa);
        }

        List<Pedido> criados = new ArrayList<>();
        for (Map.Entry<Feirante, List<Integer>> grupo : porFeirante.entrySet()) {
            Feirante feirante = grupo.getKey();
            BigDecimal subtotal = BigDecimal.ZERO;
            for (Integer idXepa : grupo.getValue()) {
                subtotal = subtotal.add(xepas.get(idXepa).getPreco().multiply(pedidos.get(idXepa)));
            }
            subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);

            // Frete: soma das feiras deste feirante que aparecem no pedido.
            BigDecimal taxaEntrega = grupo.getValue().stream()
                    .map(id -> xepas.get(id).getFeira().getNome()).distinct()
                    .map(nome -> fretes.getOrDefault(nome.toLowerCase(Locale.ROOT), BigDecimal.ZERO))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal percentual = feirante.getPlano() == null || feirante.getPlano().getPercentualComissao() == null
                    ? BigDecimal.ZERO : feirante.getPlano().getPercentualComissao();
            BigDecimal comissao = subtotal.multiply(percentual).divide(CEM, 2, RoundingMode.HALF_UP);

            Pedido pedido = new Pedido();
            pedido.setConsumidor(consumidor);
            pedido.setFeirante(feirante);
            pedido.setStatus("PAGO");
            pedido.setValorTotal(subtotal.add(taxaEntrega));
            pedido.setPercentualComissao(percentual);
            pedido.setValorComissao(comissao);
            pedido.setCodigoRetirada(String.valueOf(1000 + random.nextInt(9000)));
            pedido.setTipoRecebimento(entrega ? "ENTREGA" : "RETIRADA");
            pedido.setEnderecoEntrega(endereco);
            pedido.setTaxaEntrega(taxaEntrega);
            pedidoRepository.save(pedido);

            List<String> descricao = new ArrayList<>();
            for (Integer idXepa : grupo.getValue()) {
                Xepa xepa = xepas.get(idXepa);
                BigDecimal qtd = pedidos.get(idXepa);
                ItemPedido item = new ItemPedido();
                item.setPedido(pedido);
                item.setXepa(xepa);
                item.setQuantidade(qtd);
                item.setPrecoUnitario(xepa.getPreco());
                itemPedidoRepository.save(item);

                xepa.setQuantidade(xepa.getQuantidade().subtract(qtd));
                if (xepa.getQuantidade().signum() <= 0) xepa.setStatus("ESGOTADA");
                descricao.add(qtd.stripTrailingZeros().toPlainString() + "x " + xepa.getProduto().getNome());
            }

            Pagamento pagamento = new Pagamento();
            pagamento.setPedido(pedido);
            pagamento.setTipoPagamento(tipoPagamento);
            pagamento.setValor(pedido.getValorTotal());
            pagamento.setStatus("APROVADO");
            pagamento.setDataPagamento(LocalDateTime.now(XepaService.FUSO));
            pagamentoRepository.save(pagamento);

            lancarNoFinanceiro(pedido, descricao, xepas.get(grupo.getValue().get(0)).getFeira());
            criados.add(pedido);
        }

        itemCarrinhoRepository.removerXepas(carrinho.getId(), pedidos.keySet());
        entityManager.flush();
        criados.forEach(entityManager::refresh); // traz a data do pedido gravada pelo banco
        return respostas(criados);
    }

    // A venda entra como renda do feirante, já sem a comissão do Xepa+ (igual ao front fazia).
    private void lancarNoFinanceiro(Pedido pedido, List<String> itens, Feira feira) {
        BigDecimal liquido = pedido.getValorTotal().subtract(pedido.getValorComissao());
        if (liquido.signum() <= 0) return;
        OrigemRenda origem = origemRendaRepository.findByNomeIgnoreCase(ORIGEM_XEPA).orElseGet(() -> {
            OrigemRenda nova = new OrigemRenda();
            nova.setNome(ORIGEM_XEPA);
            return origemRendaRepository.save(nova);
        });
        String descricao = "Pedido nº " + numero(pedido) + " - " + String.join(", ", itens);
        if (descricao.length() > 150) descricao = descricao.substring(0, 147) + "...";

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

    // ---------- Consultas do consumidor ----------

    @Transactional(readOnly = true)
    public List<PedidoResponse> listar(Integer idConta) {
        Consumidor consumidor = consumidorService.consumidor(idConta);
        return respostas(pedidoRepository.findByConsumidor_IdOrderByDataPedidoDescIdDesc(consumidor.getId()));
    }

    @Transactional(readOnly = true)
    public PedidoResponse detalhe(Integer idConta, Integer idPedido) {
        Consumidor consumidor = consumidorService.consumidor(idConta);
        Pedido pedido = pedidoRepository.findByIdAndConsumidor_Id(idPedido, consumidor.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado."));
        return respostas(List.of(pedido)).get(0);
    }

    // Impacto calculado a partir dos pedidos (cancelados não contam).
    @Transactional(readOnly = true)
    public ImpactoResponse impacto(Integer idConta) {
        Consumidor consumidor = consumidorService.consumidor(idConta);
        List<Pedido> pedidos = pedidoRepository.findByConsumidor_IdOrderByDataPedidoDescIdDesc(consumidor.getId())
                .stream().filter(p -> !"CANCELADO".equals(p.getStatus())).toList();
        Map<Integer, List<ItemPedido>> itens = itensPorPedido(pedidos);

        LocalDate hoje = XepaService.hoje();
        LocalDate inicioPrimeiraSemana = hoje.minusDays(SEMANAS * 7L - 1);
        BigDecimal[] kgSemana = new BigDecimal[SEMANAS];
        Arrays.fill(kgSemana, BigDecimal.ZERO);

        BigDecimal kg = BigDecimal.ZERO;
        BigDecimal economia = BigDecimal.ZERO;
        BigDecimal quantidade = BigDecimal.ZERO;
        for (Pedido pedido : pedidos) {
            BigDecimal kgPedido = BigDecimal.ZERO;
            for (ItemPedido item : itens.getOrDefault(pedido.getId(), List.of())) {
                Produto produto = item.getXepa().getProduto();
                String categoria = Categorias.paraApp(produto.getCategoria().getNome());
                kgPedido = kgPedido.add(PESO_POR_CATEGORIA.getOrDefault(categoria, PESO_PADRAO)
                        .multiply(item.getQuantidade()));
                quantidade = quantidade.add(item.getQuantidade());
                // Economia: diferença para o preço normal; sem preço de referência, a xepa sai pela metade.
                BigDecimal referencia = produto.getPrecoReferencia();
                BigDecimal porUnidade = referencia != null && referencia.compareTo(item.getPrecoUnitario()) > 0
                        ? referencia.subtract(item.getPrecoUnitario()) : item.getPrecoUnitario();
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

    // ---------- Lado do feirante ----------

    @Transactional(readOnly = true)
    public List<PedidoResponse> vendasDoFeirante(Integer idConta) {
        Feirante feirante = feirante(idConta);
        return respostas(pedidoRepository.findByFeirante_IdOrderByIdDesc(feirante.getId()));
    }

    // O feirante confirma a retirada/entrega com o código que o consumidor mostra.
    @Transactional
    public PedidoResponse confirmarRetirada(Integer idConta, Integer idPedido, RetiradaRequest request) {
        Feirante feirante = feirante(idConta);
        Pedido pedido = pedidoRepository.findById(idPedido)
                .filter(p -> p.getFeirante().getId().equals(feirante.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado."));
        if ("CANCELADO".equals(pedido.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este pedido foi cancelado.");
        }
        String codigo = request == null || request.codigo() == null ? "" : request.codigo().trim();
        if (!codigo.equals(pedido.getCodigoRetirada())) {
            throw ConsumidorService.invalido("Código de retirada incorreto.");
        }
        pedido.setStatus("RETIRADO");
        return respostas(List.of(pedido)).get(0);
    }

    // ---------- Auxiliares ----------

    private Feirante feirante(Integer idConta) {
        return feiranteRepository.findByConta_Id(idConta)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Feirante não encontrado."));
    }

    private Map<Integer, List<ItemPedido>> itensPorPedido(List<Pedido> pedidos) {
        if (pedidos.isEmpty()) return Map.of();
        return itemPedidoRepository.dosPedidos(pedidos.stream().map(Pedido::getId).toList()).stream()
                .collect(Collectors.groupingBy(i -> i.getPedido().getId(), LinkedHashMap::new, Collectors.toList()));
    }

    private List<PedidoResponse> respostas(List<Pedido> pedidos) {
        Map<Integer, List<ItemPedido>> itens = itensPorPedido(pedidos);
        return pedidos.stream().map(p -> resposta(p, itens.getOrDefault(p.getId(), List.of()))).toList();
    }

    private static PedidoResponse resposta(Pedido pedido, List<ItemPedido> itens) {
        Feirante feirante = pedido.getFeirante();
        List<ItemPedidoResponse> itensResposta = itens.stream().map(item -> {
            Xepa xepa = item.getXepa();
            Produto produto = xepa.getProduto();
            return new ItemPedidoResponse(xepa.getId(), feirante.getConta().getId(), produto.getNome(),
                    Categorias.paraApp(produto.getCategoria().getNome()), produto.getDescricao(), produto.getImagem(),
                    feirante.getNomeFantasia(), xepa.getFeira().getNome(), XepaService.localDaFeira(xepa.getFeira()),
                    item.getQuantidade(), item.getPrecoUnitario());
        }).toList();
        BigDecimal taxa = pedido.getTaxaEntrega() == null ? BigDecimal.ZERO : pedido.getTaxaEntrega();
        boolean entrega = "ENTREGA".equals(pedido.getTipoRecebimento());
        RecebimentoResponse recebimento = new RecebimentoResponse(entrega ? "entrega" : "retirada",
                pedido.getEnderecoEntrega(), entrega ? new FreteResponse(taxa) : null);
        return new PedidoResponse(pedido.getId(), numero(pedido), pedido.getDataPedido(), statusDoApp(pedido.getStatus()),
                pedido.getStatus(), pedido.getCodigoRetirada(), pedido.getValorTotal().subtract(taxa), taxa,
                pedido.getValorTotal(), feirante.getConta().getId(), feirante.getNomeFantasia(),
                pedido.getConsumidor().getConta().getNome(), recebimento, itensResposta);
    }

    static String numero(Pedido pedido) {
        return String.format("%04d", pedido.getId());
    }

    private static String statusDoApp(String status) {
        return switch (status) {
            case "RETIRADO" -> RETIRADO;
            case "CANCELADO" -> "Cancelado";
            case "AGUARDANDO_PAGAMENTO" -> "Aguardando pagamento";
            default -> PENDENTE_RETIRADA;
        };
    }

    private TipoPagamento tipoPagamento(String metodo) {
        String nome = metodo == null || metodo.isBlank() ? "PIX" : metodo.trim().toUpperCase(Locale.ROOT);
        return tipoPagamentoRepository.findByMetodoPagamentoIgnoreCase(nome).orElseGet(() -> {
            if (!List.of("PIX", "CARTAO_CREDITO", "CARTAO_DEBITO", "DINHEIRO").contains(nome)) {
                throw ConsumidorService.invalido("Forma de pagamento inválida.");
            }
            TipoPagamento novo = new TipoPagamento();
            novo.setMetodoPagamento(nome);
            return tipoPagamentoRepository.save(novo);
        });
    }

    private static String endereco(EnderecoRequest e) {
        if (e == null || vazio(e.logradouro()) || vazio(e.numero()) || vazio(e.bairro())
                || vazio(e.cidade()) || vazio(e.uf()) || e.cep() == null
                || e.cep().replaceAll("\\D", "").length() != 8) {
            throw ConsumidorService.invalido("Preencha o endereço completo para a entrega.");
        }
        String rua = e.logradouro().trim() + ", " + e.numero().trim();
        String local = e.bairro().trim() + ", " + e.cidade().trim() + "/" + e.uf().trim().toUpperCase(Locale.ROOT);
        String texto = Stream.of(rua, vazio(e.complemento()) ? null : e.complemento().trim(), local,
                        "CEP " + e.cep().replaceAll("\\D", ""))
                .filter(Objects::nonNull).collect(Collectors.joining(" - "));
        if (texto.length() > 255) throw ConsumidorService.invalido("Endereço muito longo.");
        return texto;
    }

    private static Map<String, BigDecimal> fretes(List<FreteFeiraRequest> fretes) {
        Map<String, BigDecimal> mapa = new HashMap<>();
        if (fretes == null) return mapa;
        for (FreteFeiraRequest frete : fretes) {
            if (frete == null || vazio(frete.feira()) || frete.valor() == null) continue;
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
