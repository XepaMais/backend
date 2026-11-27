package br.com.xepamais.api.xepa;

import br.com.xepamais.api.database.model.*;
import br.com.xepamais.api.database.repository.*;
import br.com.xepamais.api.xepa.dto.XepaDtos.XepaRequest;
import br.com.xepamais.api.xepa.dto.XepaDtos.XepaResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@Service
public class XepaService {
    public static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final BigDecimal VALOR_MAXIMO = new BigDecimal("99999.99");
    private static final List<String> UNIDADES = List.of("KG", "UNIDADE", "MACO", "DUZIA", "BANDEJA");
    private static final LocalTime INICIO_PADRAO = LocalTime.of(8, 0);
    private static final LocalTime FIM_PADRAO = LocalTime.of(18, 0);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    // tb_produto só tem "descricao": guardamos "o que vem na xepa" e a descrição separados por esta linha.
    private static final String SEPARADOR = "\n---\n";

    private final XepaRepository xepaRepository;
    private final ProdutoRepository produtoRepository;
    private final CategoriaRepository categoriaRepository;
    private final FeiranteRepository feiranteRepository;
    private final FeiraRepository feiraRepository;

    public XepaService(XepaRepository xepaRepository, ProdutoRepository produtoRepository,
                       CategoriaRepository categoriaRepository, FeiranteRepository feiranteRepository,
                       FeiraRepository feiraRepository) {
        this.xepaRepository = xepaRepository;
        this.produtoRepository = produtoRepository;
        this.categoriaRepository = categoriaRepository;
        this.feiranteRepository = feiranteRepository;
        this.feiraRepository = feiraRepository;
    }

    public static LocalDate hoje() {
        return LocalDate.now(FUSO);
    }

    // Pode ser vendida agora: no ar, com estoque e dentro da validade.
    public static boolean disponivel(Xepa xepa) {
        return "DISPONIVEL".equals(xepa.getStatus())
                && xepa.getQuantidade().signum() > 0
                && xepa.getProduto().isAtivo()
                && (xepa.getValidade() == null || !xepa.getValidade().isBefore(hoje()));
    }

    // ---------- Vitrine do consumidor ----------

    @Transactional(readOnly = true)
    public List<XepaResponse> listarDisponiveis(String categoria, Integer idFeira, String busca) {
        String termo = semAcento(busca);
        Stream<Xepa> xepas = xepaRepository.disponiveis(hoje()).stream();
        if (categoria != null && !categoria.isBlank()) {
            xepas = xepas.filter(x -> Categorias.paraApp(x.getProduto().getCategoria().getNome())
                    .equalsIgnoreCase(categoria.trim()));
        }
        if (idFeira != null) {
            xepas = xepas.filter(x -> x.getFeira().getId().equals(idFeira));
        }
        if (!termo.isEmpty()) {
            xepas = xepas.filter(x -> semAcento(x.getProduto().getNome() + " " + x.getProduto().getDescricao()
                    + " " + x.getFeira().getNome()).contains(termo));
        }
        return xepas.map(XepaService::paraResposta).toList();
    }

    @Transactional(readOnly = true)
    public XepaResponse buscar(Integer id) {
        return xepaRepository.findById(id).filter(x -> !"CANCELADA".equals(x.getStatus()))
                .map(XepaService::paraResposta)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Xepa não encontrada."));
    }

    // ---------- Xepas do feirante ----------

    @Transactional(readOnly = true)
    public List<XepaResponse> listarDoFeirante(Integer idConta) {
        feirante(idConta);
        return xepaRepository.doFeirante(idConta).stream().map(XepaService::paraResposta).toList();
    }

    @Transactional
    public XepaResponse publicar(Integer idConta, XepaRequest request) {
        Feirante feirante = feirante(idConta);
        if (request == null) throw invalido("Preencha os dados da xepa.");
        String nome = texto(request.nome(), "Informe o nome da xepa.", 100);
        String nomeCategoria = Categorias.paraBanco(request.categoria());
        if (nomeCategoria == null) {
            throw invalido("Categoria inválida. Use: " + String.join(", ", Categorias.DO_APP) + ".");
        }
        if (request.idFeira() == null) throw invalido("Escolha a feira onde a xepa será retirada.");
        Feira feira = feiraRepository.doFeirante(idConta, request.idFeira())
                .orElseThrow(() -> invalido("Essa feira não está entre as suas feiras."));

        BigDecimal preco = valor(request.preco(), "Informe um preço maior que zero.");
        BigDecimal quantidade = valor(request.quantidade(), "Informe a quantidade disponível.");
        String unidade = request.unidade() == null || request.unidade().isBlank()
                ? "UNIDADE" : request.unidade().trim().toUpperCase(Locale.ROOT);
        if (!UNIDADES.contains(unidade)) {
            throw invalido("Unidade inválida. Use: " + String.join(", ", UNIDADES) + ".");
        }

        LocalDate validade = request.validade();
        if (validade != null && validade.isBefore(hoje())) throw invalido("A validade não pode estar no passado.");
        LocalDate dataRetirada = request.dataRetirada() != null ? request.dataRetirada()
                : validade != null ? validade : hoje();
        LocalTime inicio = primeiro(request.horarioRetiradaInicio(), feira.getHorarioInicio(), INICIO_PADRAO);
        LocalTime fim = primeiro(request.horarioRetiradaFim(), feira.getHorarioFim(), FIM_PADRAO);
        if (!fim.isAfter(inicio)) throw invalido("O horário final da retirada deve ser depois do inicial.");

        String imagem = request.imagem() == null ? null : request.imagem().trim();
        if (imagem != null && (!imagem.startsWith("http") || imagem.length() > 255)) {
            imagem = null; // Só guardamos endereços de imagem; fotos em base64 não cabem na coluna.
        }

        Categoria categoria = categoriaRepository.findByNomeIgnoreCase(nomeCategoria).orElseGet(() -> {
            Categoria nova = new Categoria();
            nova.setNome(nomeCategoria);
            return categoriaRepository.save(nova);
        });

        Produto produto = new Produto();
        produto.setFeirante(feirante);
        produto.setCategoria(categoria);
        produto.setNome(nome);
        produto.setDescricao(juntarDescricao(request.itens(), request.descricao()));
        produto.setImagem(imagem);
        produto.setUnidadeMedida(unidade);
        produto.setPrecoReferencia(request.precoReferencia());
        produtoRepository.save(produto);

        Xepa xepa = new Xepa();
        xepa.setProduto(produto);
        xepa.setFeira(feira);
        xepa.setPreco(preco);
        xepa.setQuantidade(quantidade);
        xepa.setValidade(validade);
        xepa.setDataRetirada(dataRetirada);
        xepa.setHorarioRetiradaInicio(inicio);
        xepa.setHorarioRetiradaFim(fim);
        xepa.setStatus("DISPONIVEL");
        return paraResposta(xepaRepository.save(xepa));
    }

    // A xepa sai da vitrine; os pedidos antigos continuam apontando para ela.
    @Transactional
    public void cancelar(Integer idConta, Integer id) {
        Xepa xepa = xepaRepository.doFeirante(idConta, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Xepa não encontrada."));
        xepa.setStatus("CANCELADA");
    }

    // ---------- Auxiliares ----------

    public static XepaResponse paraResposta(Xepa xepa) {
        Produto produto = xepa.getProduto();
        Feira feira = xepa.getFeira();
        Feirante feirante = produto.getFeirante();
        String[] partes = separarDescricao(produto.getDescricao());
        String horario = xepa.getHorarioRetiradaInicio().format(HORA) + " às "
                + xepa.getHorarioRetiradaFim().format(HORA);
        return new XepaResponse(xepa.getId(), produto.getNome(), Categorias.paraApp(produto.getCategoria().getNome()),
                partes[0], partes[1], produto.getImagem(), produto.getUnidadeMedida(), xepa.getPreco(),
                produto.getPrecoReferencia(), xepa.getQuantidade(), xepa.getValidade(), xepa.getDataRetirada(),
                xepa.getHorarioRetiradaInicio(), xepa.getHorarioRetiradaFim(), horario, xepa.getStatus(),
                feira.getId(), feira.getNome(), localDaFeira(feira), feira.getCep(),
                feira.getDias().stream().sorted().toList(), feira.getLatitude(), feira.getLongitude(),
                feirante.getConta().getId(), feirante.getConta().getNome(), feirante.getNomeFantasia());
    }

    public static String localDaFeira(Feira feira) {
        return Stream.of(feira.getEndereco(), feira.getNumero())
                .filter(s -> s != null && !s.isBlank())
                .reduce((a, b) -> a + ", " + b).orElse("");
    }

    private static String juntarDescricao(String itens, String descricao) {
        String a = itens == null ? "" : itens.trim();
        String b = descricao == null ? "" : descricao.trim();
        if (a.isEmpty() && b.isEmpty()) return null;
        return a + SEPARADOR + b;
    }

    private static String[] separarDescricao(String texto) {
        if (texto == null) return new String[]{"", ""};
        int i = texto.indexOf(SEPARADOR);
        if (i < 0) return new String[]{"", texto};
        return new String[]{texto.substring(0, i), texto.substring(i + SEPARADOR.length())};
    }

    private Feirante feirante(Integer idConta) {
        return feiranteRepository.findByConta_Id(idConta)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Feirante não encontrado."));
    }

    private static BigDecimal valor(BigDecimal valor, String mensagem) {
        if (valor == null || valor.signum() <= 0 || valor.compareTo(VALOR_MAXIMO) > 0) throw invalido(mensagem);
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    private static String texto(String valor, String mensagem, int maximo) {
        if (valor == null || valor.isBlank()) throw invalido(mensagem);
        String limpo = valor.trim();
        if (limpo.length() > maximo) throw invalido("Texto muito longo (máximo " + maximo + " caracteres).");
        return limpo;
    }

    @SafeVarargs
    private static <T> T primeiro(T... valores) {
        for (T v : valores) if (v != null) return v;
        return null;
    }

    static String semAcento(String texto) {
        if (texto == null) return "";
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).trim();
    }

    private static ResponseStatusException invalido(String mensagem) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
    }
}
