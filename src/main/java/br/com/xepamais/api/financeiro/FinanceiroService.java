package br.com.xepamais.api.financeiro;

import br.com.xepamais.api.database.model.*;
import br.com.xepamais.api.database.repository.*;
import br.com.xepamais.api.financeiro.dto.FinanceiroDtos.*;
import org.springframework.data.domain.Sort;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class FinanceiroService {

    // Só o que foi vendido pelo app fica guardado no Xepa+ e pode ser sacado
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

    // Categorias

    @Transactional(readOnly = true)
    public List<String> origensRenda() {
        List<String> nomes = new ArrayList<>();
        for (OrigemRenda origem : origemRendaRepository.findAll(Sort.by("id"))) {
            nomes.add(origem.getNome());
        }
        return nomes;
    }

    @Transactional(readOnly = true)
    public List<String> categoriasDespesa() {
        List<String> nomes = new ArrayList<>();
        for (CategoriaDespesa categoria : categoriaDespesaRepository.findAll(Sort.by("id"))) {
            nomes.add(categoria.getNome());
        }
        return nomes;
    }

    // Rendas

    @Transactional(readOnly = true)
    public List<RendaResponse> listarRendas(Integer idConta, LocalDate inicio, LocalDate fim) {
        Feirante feirante = feirante(idConta);
        List<Renda> rendas = rendaRepository.findByFeirante_IdAndDataBetweenOrderByDataDescIdDesc(
                feirante.getId(), inicioOuMinimo(inicio), fimOuMaximo(fim));
        List<RendaResponse> lista = new ArrayList<>();
        for (Renda renda : rendas) {
            lista.add(resposta(renda));
        }
        return lista;
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
        if (request == null) {
            throw erro(HttpStatus.BAD_REQUEST, "Envie os dados da renda.");
        }
        String nomeOrigem = textoObrigatorio(request.origem(), "a origem da renda", 60);
        OrigemRenda origem = origemRendaRepository.findByNomeIgnoreCase(nomeOrigem).orElse(null);
        if (origem == null) {
            throw erro(HttpStatus.BAD_REQUEST, "Origem de renda inválida.");
        }
        renda.setOrigem(origem);
        renda.setDescricao(textoObrigatorio(request.descricao(), "a descrição", 150));
        renda.setValor(valorPositivo(request.valor(), "O valor"));
        renda.setData(data(request.data()));
    }

    private Renda renda(Integer idConta, Integer id) {
        Renda renda = rendaRepository.findByIdAndFeirante_Id(id, feirante(idConta).getId()).orElse(null);
        if (renda == null) {
            throw erro(HttpStatus.NOT_FOUND, "Renda não encontrada.");
        }
        return renda;
    }

    private RendaResponse resposta(Renda renda) {
        Integer idPedido = null;
        if (renda.getPedido() != null) {
            idPedido = renda.getPedido().getId();
        }
        return new RendaResponse(renda.getId(), "renda", renda.getDescricao(), renda.getValor(), renda.getData(),
                renda.getOrigem().getNome(), nomeFeira(renda.getFeira()), idPedido);
    }

    // Despesas

    @Transactional(readOnly = true)
    public List<DespesaResponse> listarDespesas(Integer idConta, LocalDate inicio, LocalDate fim) {
        Feirante feirante = feirante(idConta);
        List<Despesa> despesas = despesaRepository.findByFeirante_IdAndDataBetweenOrderByDataDescIdDesc(
                feirante.getId(), inicioOuMinimo(inicio), fimOuMaximo(fim));
        List<DespesaResponse> lista = new ArrayList<>();
        for (Despesa despesa : despesas) {
            lista.add(resposta(despesa));
        }
        return lista;
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
        if (request == null) {
            throw erro(HttpStatus.BAD_REQUEST, "Envie os dados da despesa.");
        }
        String nomeCategoria = textoObrigatorio(request.categoria(), "a categoria", 80);
        CategoriaDespesa categoria = categoriaDespesaRepository.findByNomeIgnoreCase(nomeCategoria).orElse(null);
        if (categoria == null) {
            throw erro(HttpStatus.BAD_REQUEST, "Categoria de despesa inválida.");
        }
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
        Despesa despesa = despesaRepository.findByIdAndFeirante_Id(id, feirante(idConta).getId()).orElse(null);
        if (despesa == null) {
            throw erro(HttpStatus.NOT_FOUND, "Despesa não encontrada.");
        }
        return despesa;
    }

    private DespesaResponse resposta(Despesa despesa) {
        Integer idPedido = null;
        if (despesa.getPedido() != null) {
            idPedido = despesa.getPedido().getId();
        }
        return new DespesaResponse(despesa.getId(), despesa.getDescricao(), despesa.getQuantidade(),
                despesa.getValorUnitario(), despesa.valorTotal(), despesa.getCategoria().getNome(), despesa.getData(),
                nomeFeira(despesa.getFeira()), idPedido);
    }

    // Saques

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
        List<Saque> saques = saqueRepository.findByFeirante_IdOrderByDataSolicitacaoDescIdDesc(feirante(idConta).getId());
        List<SaqueResponse> lista = new ArrayList<>();
        for (Saque saque : saques) {
            lista.add(resposta(saque));
        }
        return lista;
    }

    @Transactional
    public SaqueResponse sacar(Integer idConta, SaqueRequest request) {
        if (request == null) {
            throw erro(HttpStatus.BAD_REQUEST, "Envie os dados do saque.");
        }
        String tipo = null;
        if (request.tipoChave() != null) {
            tipo = TIPOS_CHAVE.get(request.tipoChave().trim().toLowerCase(Locale.ROOT));
        }
        if (tipo == null) {
            throw erro(HttpStatus.BAD_REQUEST, "Tipo de chave Pix inválido.");
        }
        String chave = validarChavePix(tipo, request.chave());
        BigDecimal valor = valorPositivo(request.valor(), "O valor do saque");

        // A trava impede que dois saques ao mesmo tempo usem o mesmo saldo
        Feirante feirante = feiranteRepository.travarPorConta(idConta).orElse(null);
        if (feirante == null) {
            throw erro(HttpStatus.NOT_FOUND, "Feirante não encontrado.");
        }
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
        String chave = "";
        if (chaveRecebida != null) {
            chave = chaveRecebida.trim();
        }
        String numeros = chave.replaceAll("\\D", "");
        if (chave.isEmpty()) {
            throw erro(HttpStatus.BAD_REQUEST, "Informe a chave Pix.");
        }
        if (chave.length() > 100) {
            throw erro(HttpStatus.BAD_REQUEST, "A chave Pix é longa demais.");
        }

        if (tipo.equals("CPF")) {
            if (numeros.length() != 11) {
                throw erro(HttpStatus.BAD_REQUEST, "O CPF precisa ter 11 números.");
            }
        } else if (tipo.equals("CNPJ")) {
            if (numeros.length() != 14) {
                throw erro(HttpStatus.BAD_REQUEST, "O CNPJ precisa ter 14 números.");
            }
        } else if (tipo.equals("CELULAR")) {
            if (numeros.length() < 10 || numeros.length() > 11) {
                throw erro(HttpStatus.BAD_REQUEST, "Informe o celular com DDD.");
            }
        } else if (tipo.equals("EMAIL")) {
            if (!EMAIL.matcher(chave).matches()) {
                throw erro(HttpStatus.BAD_REQUEST, "E-mail inválido.");
            }
        } else {
            if (chave.length() < 20) {
                throw erro(HttpStatus.BAD_REQUEST, "A chave aleatória parece incompleta.");
            }
        }
        return chave;
    }

    private SaqueResponse resposta(Saque saque) {
        return new SaqueResponse(saque.getId(), saque.getValor(), saque.getTipoChavePix().toLowerCase(Locale.ROOT),
                saque.getChavePix(), saque.getStatus(), saque.getDataSolicitacao());
    }

    // Resumo e planilha

    @Transactional(readOnly = true)
    public ResumoResponse resumo(Integer idConta) {
        Feirante feirante = feirante(idConta);
        LocalDate hoje = LocalDate.now(FUSO);
        LocalDate inicioSemana = hoje.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate fimSemana = inicioSemana.plusDays(6);
        LocalDate inicioMes = hoje.withDayOfMonth(1);
        LocalDate fimMes = hoje.with(TemporalAdjusters.lastDayOfMonth());

        // Busca do menor ao maior dia dos dois períodos
        LocalDate de = inicioMes;
        if (inicioSemana.isBefore(inicioMes)) {
            de = inicioSemana;
        }
        LocalDate ate = fimMes;
        if (fimSemana.isAfter(fimMes)) {
            ate = fimSemana;
        }

        List<Renda> rendas = rendaRepository.findByFeirante_IdAndDataBetweenOrderByDataDescIdDesc(feirante.getId(), de, ate);
        List<Despesa> despesas = despesaRepository.findByFeirante_IdAndDataBetweenOrderByDataDescIdDesc(feirante.getId(), de, ate);

        BigDecimal rendaHoje = somarRendas(rendas, hoje, hoje);
        BigDecimal rendaSemana = somarRendas(rendas, inicioSemana, fimSemana);
        BigDecimal rendaMes = somarRendas(rendas, inicioMes, fimMes);
        BigDecimal despesaHoje = somarDespesas(despesas, hoje, hoje);
        BigDecimal despesaSemana = somarDespesas(despesas, inicioSemana, fimSemana);
        BigDecimal despesaMes = somarDespesas(despesas, inicioMes, fimMes);

        Totais totalRendas = new Totais(rendaHoje, rendaSemana, rendaMes);
        Totais totalDespesas = new Totais(despesaHoje, despesaSemana, despesaMes);
        Totais lucro = new Totais(rendaHoje.subtract(despesaHoje), rendaSemana.subtract(despesaSemana),
                rendaMes.subtract(despesaMes));
        return new ResumoResponse(totalRendas, totalDespesas, lucro);
    }

    // tipo: "todos", "lucro" ou "gasto". categoria e busca são opcionais
    @Transactional(readOnly = true)
    public HistoricoResponse historico(Integer idConta, LocalDate inicio, LocalDate fim, String tipo,
                                       String categoria, String busca) {
        Feirante feirante = feirante(idConta);
        LocalDate de = inicioOuMinimo(inicio);
        LocalDate ate = fimOuMaximo(fim);
        String tipoFiltro = "todos";
        if (tipo != null && !tipo.isBlank()) {
            tipoFiltro = tipo.trim().toLowerCase(Locale.ROOT);
        }
        if (!tipoFiltro.equals("todos") && !tipoFiltro.equals("lucro") && !tipoFiltro.equals("gasto")) {
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

        Locale ptBr = Locale.forLanguageTag("pt-BR");
        String termo = "";
        if (busca != null) {
            termo = busca.trim().toLowerCase(ptBr);
        }
        List<RegistroHistorico> filtrados = new ArrayList<>();
        for (RegistroHistorico r : registros) {
            boolean categoriaOk = categoria == null || categoria.isBlank() || r.categoria().equalsIgnoreCase(categoria.trim());
            boolean buscaOk = r.descricao().toLowerCase(ptBr).contains(termo);
            if (categoriaOk && buscaOk) {
                filtrados.add(r);
            }
        }

        // Ordena do mais novo para o mais antigo
        for (int i = 1; i < filtrados.size(); i++) {
            int j = i;
            while (j > 0 && filtrados.get(j - 1).data().isBefore(filtrados.get(j).data())) {
                RegistroHistorico temp = filtrados.get(j);
                filtrados.set(j, filtrados.get(j - 1));
                filtrados.set(j - 1, temp);
                j--;
            }
        }

        BigDecimal lucros = BigDecimal.ZERO;
        BigDecimal gastos = BigDecimal.ZERO;
        for (RegistroHistorico registro : filtrados) {
            if (registro.tipo().equals("lucro")) {
                lucros = lucros.add(registro.valor());
            } else {
                gastos = gastos.add(registro.valor());
            }
        }
        return new HistoricoResponse(filtrados, dinheiro(lucros), dinheiro(gastos), dinheiro(lucros.subtract(gastos)));
    }

    @Transactional(readOnly = true)
    public String nomeDoFeirante(Integer idConta) {
        Feirante feirante = feirante(idConta);
        return feirante.getConta().getNome();
    }

    // Apoio

    private Feirante feirante(Integer idConta) {
        if (idConta == null) {
            throw erro(HttpStatus.BAD_REQUEST, "Conta não informada.");
        }
        Feirante feirante = feiranteRepository.findByConta_Id(idConta).orElse(null);
        if (feirante == null) {
            throw erro(HttpStatus.NOT_FOUND, "Feirante não encontrado.");
        }
        return feirante;
    }

    private static BigDecimal somarRendas(List<Renda> rendas, LocalDate de, LocalDate ate) {
        BigDecimal total = BigDecimal.ZERO;
        for (Renda r : rendas) {
            if (entre(r.getData(), de, ate)) {
                total = total.add(r.getValor());
            }
        }
        return dinheiro(total);
    }

    private static BigDecimal somarDespesas(List<Despesa> despesas, LocalDate de, LocalDate ate) {
        BigDecimal total = BigDecimal.ZERO;
        for (Despesa d : despesas) {
            if (entre(d.getData(), de, ate)) {
                total = total.add(d.valorTotal());
            }
        }
        return dinheiro(total);
    }

    private static boolean entre(LocalDate data, LocalDate de, LocalDate ate) {
        return !data.isBefore(de) && !data.isAfter(ate);
    }

    private static String nomeFeira(Feira feira) {
        if (feira == null) {
            return "";
        }
        return feira.getNome();
    }

    private static LocalDate inicioOuMinimo(LocalDate data) {
        if (data == null) {
            return DATA_MINIMA;
        }
        return data;
    }

    private static LocalDate fimOuMaximo(LocalDate data) {
        if (data == null) {
            return DATA_MAXIMA;
        }
        return data;
    }

    private static LocalDate data(LocalDate data) {
        LocalDate valor = data;
        if (valor == null) {
            valor = LocalDate.now(FUSO);
        }
        if (valor.isBefore(DATA_MINIMA) || valor.isAfter(LocalDate.now(FUSO).plusYears(1))) {
            throw erro(HttpStatus.BAD_REQUEST, "Data inválida.");
        }
        return valor;
    }

    private static String textoObrigatorio(String texto, String nome, int tamanhoMaximo) {
        if (texto == null || texto.isBlank()) {
            throw erro(HttpStatus.BAD_REQUEST, "Informe " + nome + ".");
        }
        String limpo = texto.trim();
        if (limpo.length() > tamanhoMaximo) {
            throw erro(HttpStatus.BAD_REQUEST, "O campo " + nome + " aceita até " + tamanhoMaximo + " caracteres.");
        }
        return limpo;
    }

    private static BigDecimal valorPositivo(BigDecimal valor, String nome) {
        if (valor == null || valor.signum() <= 0) {
            throw erro(HttpStatus.BAD_REQUEST, nome + " precisa ser maior que zero.");
        }
        BigDecimal arredondado = valor.setScale(2, RoundingMode.HALF_UP);
        if (arredondado.signum() <= 0) {
            throw erro(HttpStatus.BAD_REQUEST, nome + " precisa ser maior que zero.");
        }
        if (arredondado.compareTo(VALOR_MAXIMO) > 0) {
            throw erro(HttpStatus.BAD_REQUEST, nome + " é alto demais.");
        }
        return arredondado;
    }

    private static BigDecimal dinheiro(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    private static ResponseStatusException erro(HttpStatus status, String mensagem) {
        return new ResponseStatusException(status, mensagem);
    }
}
