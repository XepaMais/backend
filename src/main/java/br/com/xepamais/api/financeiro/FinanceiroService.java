package br.com.xepamais.api.financeiro;

import br.com.xepamais.api.database.model.*;
import br.com.xepamais.api.database.repository.*;
import br.com.xepamais.api.financeiro.dto.FinanceiroDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class FinanceiroService {

    // Só o que foi vendido pelo app fica guardado no Xepa+ e pode ser sacado.
    static final String ORIGEM_XEPA = "Venda de xepa pelo Xepa+";
    static final String CATEGORIA_TAXA_XEPA = "Taxa do Xepa+";

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate DATA_MINIMA = LocalDate.of(2000, 1, 1);
    private static final LocalDate DATA_MAXIMA = LocalDate.of(2999, 12, 31);
    private static final BigDecimal VALOR_MAXIMO = new BigDecimal("99999999.99");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Map<String, String> TIPOS_CHAVE = Map.of(
            "cpf", "CPF", "cnpj", "CNPJ", "email", "EMAIL", "celular", "CELULAR", "aleatoria", "ALEATORIA");

    private final FeiranteRepository feiranteRepository;
    private final RendaRepository rendaRepository;
    private final DespesaRepository despesaRepository;
    private final SaqueRepository saqueRepository;
    private final OrigemRendaRepository origemRendaRepository;
    private final CategoriaDespesaRepository categoriaDespesaRepository;

    public FinanceiroService(FeiranteRepository feiranteRepository, RendaRepository rendaRepository,
                             DespesaRepository despesaRepository, SaqueRepository saqueRepository,
                             OrigemRendaRepository origemRendaRepository,
                             CategoriaDespesaRepository categoriaDespesaRepository) {
        this.feiranteRepository = feiranteRepository;
        this.rendaRepository = rendaRepository;
        this.despesaRepository = despesaRepository;
        this.saqueRepository = saqueRepository;
        this.origemRendaRepository = origemRendaRepository;
        this.categoriaDespesaRepository = categoriaDespesaRepository;
    }

    //  Categorias 

    @Transactional(readOnly = true)
    public List<String> origensRenda() {
        return origemRendaRepository.findAll().stream()
                .sorted(Comparator.comparing(OrigemRenda::getId))
                .map(OrigemRenda::getNome).toList();
    }

    @Transactional(readOnly = true)
    public List<String> categoriasDespesa() {
        return categoriaDespesaRepository.findAll().stream()
                .sorted(Comparator.comparing(CategoriaDespesa::getId))
                .map(CategoriaDespesa::getNome).toList();
    }

    //  Rendas (lucros) 

    @Transactional(readOnly = true)
    public List<RendaResponse> listarRendas(Integer idConta, LocalDate inicio, LocalDate fim) {
        Feirante feirante = feirante(idConta);
        return rendaRepository.findByFeirante_IdAndDataBetweenOrderByDataDescIdDesc(
                        feirante.getId(), inicioOuMinimo(inicio), fimOuMaximo(fim))
                .stream().map(this::resposta).toList();
    }

    @Transactional
    public RendaResponse criarRenda(Integer idConta, RendaRequest request) {
        Renda renda = new Renda();
        renda.setFeirante(feirante(idConta));
        preencher(renda, request);
        return resposta(rendaRepository.save(renda));
    }

    @Transactional
    public RendaResponse atualizarRenda(Integer idConta, Integer id, RendaRequest request) {
        Renda renda = renda(idConta, id);
        if (renda.getPedido() != null) {
            throw erro(HttpStatus.CONFLICT, "Rendas de pedidos do app são lançadas automaticamente e não podem ser editadas.");
        }
        preencher(renda, request);
        return resposta(renda);
    }

    @Transactional
    public void excluirRenda(Integer idConta, Integer id) {
        Renda renda = renda(idConta, id);
        if (renda.getPedido() != null) {
            throw erro(HttpStatus.CONFLICT, "Rendas de pedidos do app não podem ser excluídas.");
        }
        rendaRepository.delete(renda);
    }

    private void preencher(Renda renda, RendaRequest request) {
        if (request == null) throw erro(HttpStatus.BAD_REQUEST, "Envie os dados da renda.");
        OrigemRenda origem = origemRendaRepository.findByNomeIgnoreCase(textoObrigatorio(request.origem(), "a origem da renda", 60))
                .orElseThrow(() -> erro(HttpStatus.BAD_REQUEST, "Origem de renda inválida."));
        renda.setOrigem(origem);
        renda.setDescricao(textoObrigatorio(request.descricao(), "a descrição", 150));
        renda.setValor(valorPositivo(request.valor(), "O valor"));
        renda.setData(data(request.data()));
    }

    private Renda renda(Integer idConta, Integer id) {
        return rendaRepository.findByIdAndFeirante_Id(id, feirante(idConta).getId())
                .orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Renda não encontrada."));
    }

    private RendaResponse resposta(Renda renda) {
        return new RendaResponse(renda.getId(), "renda", renda.getDescricao(), renda.getValor(), renda.getData(),
                renda.getOrigem().getNome(), nomeFeira(renda.getFeira()),
                renda.getPedido() == null ? null : renda.getPedido().getId());
    }

    //  Despesas (gastos) 

    @Transactional(readOnly = true)
    public List<DespesaResponse> listarDespesas(Integer idConta, LocalDate inicio, LocalDate fim) {
        Feirante feirante = feirante(idConta);
        return despesaRepository.findByFeirante_IdAndDataBetweenOrderByDataDescIdDesc(
                        feirante.getId(), inicioOuMinimo(inicio), fimOuMaximo(fim))
                .stream().map(this::resposta).toList();
    }

    @Transactional
    public DespesaResponse criarDespesa(Integer idConta, DespesaRequest request) {
        Despesa despesa = new Despesa();
        despesa.setFeirante(feirante(idConta));
        preencher(despesa, request);
        return resposta(despesaRepository.save(despesa));
    }

    @Transactional
    public DespesaResponse atualizarDespesa(Integer idConta, Integer id, DespesaRequest request) {
        Despesa despesa = despesa(idConta, id);
        if (despesa.getPedido() != null) {
            throw erro(HttpStatus.CONFLICT, "A taxa do Xepa+ é lançada automaticamente e não pode ser editada.");
        }
        preencher(despesa, request);
        return resposta(despesa);
    }

    @Transactional
    public void excluirDespesa(Integer idConta, Integer id) {
        Despesa despesa = despesa(idConta, id);
        if (despesa.getPedido() != null) {
            throw erro(HttpStatus.CONFLICT, "A taxa do Xepa+ não pode ser excluída.");
        }
        despesaRepository.delete(despesa);
    }

    private void preencher(Despesa despesa, DespesaRequest request) {
        if (request == null) throw erro(HttpStatus.BAD_REQUEST, "Envie os dados da despesa.");
        CategoriaDespesa categoria = categoriaDespesaRepository
                .findByNomeIgnoreCase(textoObrigatorio(request.categoria(), "a categoria", 80))
                .orElseThrow(() -> erro(HttpStatus.BAD_REQUEST, "Categoria de despesa inválida."));
        BigDecimal quantidade = valorPositivo(request.quantidade(), "A quantidade");
        BigDecimal valorUnitario = valorPositivo(request.valorUnitario(), "O valor unitário");
        if (quantidade.multiply(valorUnitario).compareTo(VALOR_MAXIMO) > 0) {
            throw erro(HttpStatus.BAD_REQUEST, "O valor total da despesa é alto demais.");
        }
        despesa.setCategoria(categoria);
        despesa.setDescricao(textoObrigatorio(request.descricao(), "a descrição", 150));
        despesa.setQuantidade(quantidade);
        despesa.setValorUnitario(valorUnitario);
        despesa.setData(data(request.data()));
    }

    private Despesa despesa(Integer idConta, Integer id) {
        return despesaRepository.findByIdAndFeirante_Id(id, feirante(idConta).getId())
                .orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Despesa não encontrada."));
    }

    private DespesaResponse resposta(Despesa despesa) {
        return new DespesaResponse(despesa.getId(), despesa.getDescricao(), despesa.getQuantidade(),
                despesa.getValorUnitario(), despesa.valorTotal(), despesa.getCategoria().getNome(), despesa.getData(),
                nomeFeira(despesa.getFeira()), despesa.getPedido() == null ? null : despesa.getPedido().getId());
    }

    //  Saques 

    @Transactional(readOnly = true)
    public SaldoResponse saldo(Integer idConta) {
        return saldo(feirante(idConta));
    }

    private SaldoResponse saldo(Feirante feirante) {
        BigDecimal recebido = rendaRepository.somarPorOrigem(feirante.getId(), ORIGEM_XEPA);
        BigDecimal sacado = saqueRepository.somarNaoRecusados(feirante.getId());
        return new SaldoResponse(dinheiro(recebido), dinheiro(sacado), dinheiro(recebido.subtract(sacado)));
    }

    @Transactional(readOnly = true)
    public List<SaqueResponse> listarSaques(Integer idConta) {
        return saqueRepository.findByFeirante_IdOrderByDataSolicitacaoDescIdDesc(feirante(idConta).getId())
                .stream().map(this::resposta).toList();
    }

    @Transactional
    public SaqueResponse sacar(Integer idConta, SaqueRequest request) {
        if (request == null) throw erro(HttpStatus.BAD_REQUEST, "Envie os dados do saque.");
        String tipo = request.tipoChave() == null ? null : TIPOS_CHAVE.get(request.tipoChave().trim().toLowerCase(Locale.ROOT));
        if (tipo == null) throw erro(HttpStatus.BAD_REQUEST, "Tipo de chave Pix inválido.");
        String chave = validarChavePix(tipo, request.chave());
        BigDecimal valor = valorPositivo(request.valor(), "O valor do saque");

        // A trava impede que dois saques simultâneos usem o mesmo saldo.
        Feirante feirante = feiranteRepository.travarPorConta(idConta)
                .orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Feirante não encontrado."));
        if (valor.compareTo(saldo(feirante).disponivel()) > 0) {
            throw erro(HttpStatus.UNPROCESSABLE_CONTENT, "O valor é maior que o seu saldo disponível.");
        }

        Saque saque = new Saque();
        saque.setFeirante(feirante);
        saque.setValor(valor);
        saque.setTipoChavePix(tipo);
        saque.setChavePix(chave);
        saque.setStatus("SOLICITADO");
        saque.setDataSolicitacao(LocalDateTime.now(FUSO));
        return resposta(saqueRepository.save(saque));
    }

    private String validarChavePix(String tipo, String chaveRecebida) {
        String chave = chaveRecebida == null ? "" : chaveRecebida.trim();
        String numeros = chave.replaceAll("\\D", "");
        if (chave.isEmpty()) throw erro(HttpStatus.BAD_REQUEST, "Informe a chave Pix.");
        if (chave.length() > 100) throw erro(HttpStatus.BAD_REQUEST, "A chave Pix é longa demais.");
        switch (tipo) {
            case "CPF" -> { if (numeros.length() != 11) throw erro(HttpStatus.BAD_REQUEST, "O CPF precisa ter 11 números."); }
            case "CNPJ" -> { if (numeros.length() != 14) throw erro(HttpStatus.BAD_REQUEST, "O CNPJ precisa ter 14 números."); }
            case "CELULAR" -> {
                if (numeros.length() < 10 || numeros.length() > 11) throw erro(HttpStatus.BAD_REQUEST, "Informe o celular com DDD.");
            }
            case "EMAIL" -> { if (!EMAIL.matcher(chave).matches()) throw erro(HttpStatus.BAD_REQUEST, "E-mail inválido."); }
            default -> { if (chave.length() < 20) throw erro(HttpStatus.BAD_REQUEST, "A chave aleatória parece incompleta."); }
        }
        return chave;
    }

    private SaqueResponse resposta(Saque saque) {
        return new SaqueResponse(saque.getId(), saque.getValor(), saque.getTipoChavePix().toLowerCase(Locale.ROOT),
                saque.getChavePix(), saque.getStatus(), saque.getDataSolicitacao());
    }

    //  Resumo e planilha 

    @Transactional(readOnly = true)
    public ResumoResponse resumo(Integer idConta) {
        Feirante feirante = feirante(idConta);
        LocalDate hoje = LocalDate.now(FUSO);
        LocalDate inicioSemana = hoje.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate fimSemana = inicioSemana.plusDays(6);
        LocalDate inicioMes = hoje.withDayOfMonth(1);
        LocalDate fimMes = hoje.with(TemporalAdjusters.lastDayOfMonth());
        // Busca do menor ao maior dia que aparece em algum dos períodos.
        LocalDate de = inicioSemana.isBefore(inicioMes) ? inicioSemana : inicioMes;
        LocalDate ate = fimSemana.isAfter(fimMes) ? fimSemana : fimMes;

        List<Renda> rendas = rendaRepository.findByFeirante_IdAndDataBetweenOrderByDataDescIdDesc(feirante.getId(), de, ate);
        List<Despesa> despesas = despesaRepository.findByFeirante_IdAndDataBetweenOrderByDataDescIdDesc(feirante.getId(), de, ate);

        Totais totalRendas = new Totais(
                somarRendas(rendas, hoje, hoje), somarRendas(rendas, inicioSemana, fimSemana), somarRendas(rendas, inicioMes, fimMes));
        Totais totalDespesas = new Totais(
                somarDespesas(despesas, hoje, hoje), somarDespesas(despesas, inicioSemana, fimSemana),
                somarDespesas(despesas, inicioMes, fimMes));
        Totais lucro = new Totais(
                totalRendas.hoje().subtract(totalDespesas.hoje()),
                totalRendas.semana().subtract(totalDespesas.semana()),
                totalRendas.mes().subtract(totalDespesas.mes()));
        return new ResumoResponse(totalRendas, totalDespesas, lucro);
    }

    // tipo: "todos", "lucro" ou "gasto". categoria e busca são opcionais.
    @Transactional(readOnly = true)
    public HistoricoResponse historico(Integer idConta, LocalDate inicio, LocalDate fim, String tipo,
                                       String categoria, String busca) {
        Feirante feirante = feirante(idConta);
        LocalDate de = inicioOuMinimo(inicio);
        LocalDate ate = fimOuMaximo(fim);
        String tipoFiltro = tipo == null || tipo.isBlank() ? "todos" : tipo.trim().toLowerCase(Locale.ROOT);
        if (!List.of("todos", "lucro", "gasto").contains(tipoFiltro)) {
            throw erro(HttpStatus.BAD_REQUEST, "Tipo de movimentação inválido.");
        }

        List<RegistroHistorico> registros = new ArrayList<>();
        if (!tipoFiltro.equals("gasto")) {
            for (Renda renda : rendaRepository.findByFeirante_IdAndDataBetweenOrderByDataDescIdDesc(feirante.getId(), de, ate)) {
                registros.add(new RegistroHistorico("renda-" + renda.getId(), renda.getData(), renda.getDescricao(),
                        renda.getOrigem().getNome(), "lucro", renda.getValor(), nomeFeira(renda.getFeira())));
            }
        }
        if (!tipoFiltro.equals("lucro")) {
            for (Despesa despesa : despesaRepository.findByFeirante_IdAndDataBetweenOrderByDataDescIdDesc(feirante.getId(), de, ate)) {
                registros.add(new RegistroHistorico("despesa-" + despesa.getId(), despesa.getData(), despesa.getDescricao(),
                        despesa.getCategoria().getNome(), "gasto", despesa.valorTotal(), nomeFeira(despesa.getFeira())));
            }
        }

        String termo = busca == null ? "" : busca.trim().toLowerCase(Locale.forLanguageTag("pt-BR"));
        List<RegistroHistorico> filtrados = registros.stream()
                .filter(r -> categoria == null || categoria.isBlank() || r.categoria().equalsIgnoreCase(categoria.trim()))
                .filter(r -> r.descricao().toLowerCase(Locale.forLanguageTag("pt-BR")).contains(termo))
                .sorted(Comparator.comparing(RegistroHistorico::data).reversed())
                .toList();

        BigDecimal lucros = BigDecimal.ZERO;
        BigDecimal gastos = BigDecimal.ZERO;
        for (RegistroHistorico registro : filtrados) {
            if (registro.tipo().equals("lucro")) lucros = lucros.add(registro.valor());
            else gastos = gastos.add(registro.valor());
        }
        return new HistoricoResponse(filtrados, dinheiro(lucros), dinheiro(gastos), dinheiro(lucros.subtract(gastos)));
    }

    @Transactional(readOnly = true)
    public String nomeDoFeirante(Integer idConta) {
        Feirante feirante = feirante(idConta);
        return feirante.getConta().getNome();
    }

    //  Apoio 

    private Feirante feirante(Integer idConta) {
        if (idConta == null) throw erro(HttpStatus.BAD_REQUEST, "Conta não informada.");
        return feiranteRepository.findByConta_Id(idConta)
                .orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Feirante não encontrado."));
    }

    private static BigDecimal somarRendas(List<Renda> rendas, LocalDate de, LocalDate ate) {
        return dinheiro(rendas.stream().filter(r -> entre(r.getData(), de, ate))
                .map(Renda::getValor).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static BigDecimal somarDespesas(List<Despesa> despesas, LocalDate de, LocalDate ate) {
        return dinheiro(despesas.stream().filter(d -> entre(d.getData(), de, ate))
                .map(Despesa::valorTotal).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static boolean entre(LocalDate data, LocalDate de, LocalDate ate) {
        return !data.isBefore(de) && !data.isAfter(ate);
    }

    private static String nomeFeira(Feira feira) {
        return feira == null ? "" : feira.getNome();
    }

    private static LocalDate inicioOuMinimo(LocalDate data) {
        return data == null ? DATA_MINIMA : data;
    }

    private static LocalDate fimOuMaximo(LocalDate data) {
        return data == null ? DATA_MAXIMA : data;
    }

    private static LocalDate data(LocalDate data) {
        LocalDate valor = data == null ? LocalDate.now(FUSO) : data;
        if (valor.isBefore(DATA_MINIMA) || valor.isAfter(LocalDate.now(FUSO).plusYears(1))) {
            throw erro(HttpStatus.BAD_REQUEST, "Data inválida.");
        }
        return valor;
    }

    private static String textoObrigatorio(String texto, String nome, int tamanhoMaximo) {
        if (texto == null || texto.isBlank()) throw erro(HttpStatus.BAD_REQUEST, "Informe " + nome + ".");
        String limpo = texto.trim();
        if (limpo.length() > tamanhoMaximo) {
            throw erro(HttpStatus.BAD_REQUEST, "O campo " + nome + " aceita até " + tamanhoMaximo + " caracteres.");
        }
        return limpo;
    }

    private static BigDecimal valorPositivo(BigDecimal valor, String nome) {
        if (valor == null || valor.signum() <= 0) throw erro(HttpStatus.BAD_REQUEST, nome + " precisa ser maior que zero.");
        BigDecimal arredondado = valor.setScale(2, RoundingMode.HALF_UP);
        if (arredondado.signum() <= 0) throw erro(HttpStatus.BAD_REQUEST, nome + " precisa ser maior que zero.");
        if (arredondado.compareTo(VALOR_MAXIMO) > 0) throw erro(HttpStatus.BAD_REQUEST, nome + " é alto demais.");
        return arredondado;
    }

    private static BigDecimal dinheiro(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    private static ResponseStatusException erro(HttpStatus status, String mensagem) {
        return new ResponseStatusException(status, mensagem);
    }
}
