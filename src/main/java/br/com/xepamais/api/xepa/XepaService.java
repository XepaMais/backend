package br.com.xepamais.api.xepa;

import br.com.xepamais.api.database.model.*;
import br.com.xepamais.api.database.repository.*;
import br.com.xepamais.api.imagem.ImagemController;
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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Service
public class XepaService {
    public static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final BigDecimal VALOR_MAXIMO = new BigDecimal("99999.99");
    private static final List<String> UNIDADES = List.of("KG", "UNIDADE", "MACO", "DUZIA", "BANDEJA");
    private static final LocalTime INICIO_PADRAO = LocalTime.of(8, 0);
    private static final LocalTime FIM_PADRAO = LocalTime.of(18, 0);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    // tb_produto só tem "descricao", então os itens e a descrição ficam juntos separados por isso
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

    // Pode ser vendida agora: no ar, com estoque e dentro da validade
    public static boolean disponivel(Xepa xepa) {
        if (!"DISPONIVEL".equals(xepa.getStatus())) {
            return false;
        }
        if (xepa.getQuantidade().signum() <= 0) {
            return false;
        }
        if (!xepa.getProduto().isAtivo()) {
            return false;
        }
        if (xepa.getValidade() != null && xepa.getValidade().isBefore(hoje())) {
            return false;
        }
        return true;
    }

    // Vitrine do consumidor

    @Transactional(readOnly = true)
    public List<XepaResponse> listarDisponiveis(String categoria, Integer idFeira, String busca) {
        String termo = semAcento(busca);
        List<XepaResponse> lista = new ArrayList<>();
        for (Xepa x : xepaRepository.disponiveis(hoje())) {
            if (categoria != null && !categoria.isBlank()) {
                String categoriaXepa = Categorias.paraApp(x.getProduto().getCategoria().getNome());
                if (!categoriaXepa.equalsIgnoreCase(categoria.trim())) {
                    continue;
                }
            }
            if (idFeira != null && !x.getFeira().getId().equals(idFeira)) {
                continue;
            }
            if (!termo.isEmpty()) {
                String textoXepa = x.getProduto().getNome() + " " + x.getProduto().getDescricao() + " " + x.getFeira().getNome();
                if (!semAcento(textoXepa).contains(termo)) {
                    continue;
                }
            }
            lista.add(paraResposta(x));
        }
        return lista;
    }

    @Transactional(readOnly = true)
    public XepaResponse buscar(Integer id) {
        Xepa xepa = xepaRepository.findById(id).orElse(null);
        if (xepa == null || "CANCELADA".equals(xepa.getStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Xepa não encontrada.");
        }
        return paraResposta(xepa);
    }

    // Xepas do feirante

    @Transactional(readOnly = true)
    public List<XepaResponse> listarDoFeirante(Integer idConta) {
        feirante(idConta);
        List<XepaResponse> lista = new ArrayList<>();
        for (Xepa x : xepaRepository.doFeirante(idConta)) {
            lista.add(paraResposta(x));
        }
        return lista;
    }

    @Transactional
    public XepaResponse publicar(Integer idConta, XepaRequest request) {
        Feirante feirante = feirante(idConta);
        if (request == null) {
            throw invalido("Preencha os dados da xepa.");
        }
        String nome = texto(request.nome(), "Informe o nome da xepa.", 100);
        String nomeCategoria = Categorias.paraBanco(request.categoria());
        if (nomeCategoria == null) {
            throw invalido("Categoria inválida. Use: " + String.join(", ", Categorias.DO_APP) + ".");
        }
        if (request.idFeira() == null) {
            throw invalido("Escolha a feira onde a xepa será retirada.");
        }
        Feira feira = feiraRepository.doFeirante(idConta, request.idFeira()).orElse(null);
        if (feira == null) {
            throw invalido("Essa feira não está entre as suas feiras.");
        }

        BigDecimal preco = valor(request.preco(), "Informe um preço maior que zero.");
        BigDecimal quantidade = valor(request.quantidade(), "Informe a quantidade disponível.");
        String unidade = "UNIDADE";
        if (request.unidade() != null && !request.unidade().isBlank()) {
            unidade = request.unidade().trim().toUpperCase(Locale.ROOT);
        }
        if (!UNIDADES.contains(unidade)) {
            throw invalido("Unidade inválida. Use: " + String.join(", ", UNIDADES) + ".");
        }

        LocalDate validade = request.validade();
        if (validade != null && validade.isBefore(hoje())) {
            throw invalido("A validade não pode estar no passado.");
        }
        LocalDate dataRetirada = request.dataRetirada();
        if (dataRetirada == null) {
            if (validade != null) {
                dataRetirada = validade;
            } else {
                dataRetirada = hoje();
            }
        }

        LocalTime inicio = request.horarioRetiradaInicio();
        if (inicio == null) {
            inicio = feira.getHorarioInicio();
        }
        if (inicio == null) {
            inicio = INICIO_PADRAO;
        }
        LocalTime fim = request.horarioRetiradaFim();
        if (fim == null) {
            fim = feira.getHorarioFim();
        }
        if (fim == null) {
            fim = FIM_PADRAO;
        }
        if (!fim.isAfter(inicio)) {
            throw invalido("O horário final da retirada deve ser depois do inicial.");
        }

        // A foto já foi enviada antes em POST /api/imagens
        String imagem = ImagemController.enderecoValido(request.imagem());

        Produto produto = new Produto();
        produto.setFeirante(feirante);
        produto.setCategoria(categoria(nomeCategoria));
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
        xepa.setDataCadastro(LocalDateTime.now(FUSO));
        return paraResposta(xepaRepository.save(xepa));
    }

    // O feirante corrige a xepa (só muda o que veio preenchido)
    @Transactional
    public XepaResponse editar(Integer idConta, Integer id, XepaRequest request) {
        Xepa xepa = xepaRepository.doFeirante(idConta, id).orElse(null);
        if (xepa == null || "CANCELADA".equals(xepa.getStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Xepa não encontrada.");
        }
        if (request == null) {
            throw invalido("Informe o que mudou na xepa.");
        }
        Produto produto = xepa.getProduto();

        if (request.nome() != null) {
            produto.setNome(texto(request.nome(), "Informe o nome da xepa.", 100));
        }
        if (request.categoria() != null) {
            String nomeCategoria = Categorias.paraBanco(request.categoria());
            if (nomeCategoria == null) {
                throw invalido("Categoria inválida. Use: " + String.join(", ", Categorias.DO_APP) + ".");
            }
            produto.setCategoria(categoria(nomeCategoria));
        }
        if (request.itens() != null || request.descricao() != null) {
            String[] atual = separarDescricao(produto.getDescricao());
            String itens = atual[0];
            String descricao = atual[1];
            if (request.itens() != null) {
                itens = request.itens();
            }
            if (request.descricao() != null) {
                descricao = request.descricao();
            }
            produto.setDescricao(juntarDescricao(itens, descricao));
        }
        if (request.imagem() != null) {
            String imagem = ImagemController.enderecoValido(request.imagem());
            if (imagem == null && !request.imagem().isBlank()) {
                throw invalido("Envie a foto antes em /api/imagens.");
            }
            produto.setImagem(imagem);
        }
        if (request.precoReferencia() != null) {
            produto.setPrecoReferencia(request.precoReferencia());
        }
        if (request.preco() != null) {
            xepa.setPreco(valor(request.preco(), "Informe um preço maior que zero."));
        }
        if (request.quantidade() != null) {
            if (request.quantidade().signum() < 0 || request.quantidade().compareTo(VALOR_MAXIMO) > 0) {
                throw invalido("Informe uma quantidade válida.");
            }
            xepa.setQuantidade(request.quantidade().setScale(2, RoundingMode.HALF_UP));
        }
        if (request.validade() != null) {
            if (request.validade().isBefore(hoje())) {
                throw invalido("A validade não pode estar no passado.");
            }
            xepa.setValidade(request.validade());
        }
        if (request.idFeira() != null && !request.idFeira().equals(xepa.getFeira().getId())) {
            Feira feira = feiraRepository.doFeirante(idConta, request.idFeira()).orElse(null);
            if (feira == null) {
                throw invalido("Essa feira não está entre as suas feiras.");
            }
            xepa.setFeira(feira);
        }
        if (xepa.getQuantidade().signum() > 0) {
            xepa.setStatus("DISPONIVEL");
        } else {
            xepa.setStatus("ESGOTADA");
        }
        return paraResposta(xepa);
    }

    // A xepa sai da vitrine; os pedidos antigos continuam apontando para ela
    @Transactional
    public void cancelar(Integer idConta, Integer id) {
        Xepa xepa = xepaRepository.doFeirante(idConta, id).orElse(null);
        if (xepa == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Xepa não encontrada.");
        }
        xepa.setStatus("CANCELADA");
    }

    // Auxiliares

    public static XepaResponse paraResposta(Xepa xepa) {
        Produto produto = xepa.getProduto();
        Feira feira = xepa.getFeira();
        Feirante feirante = produto.getFeirante();
        String[] partes = separarDescricao(produto.getDescricao());
        String horario = xepa.getHorarioRetiradaInicio().format(HORA) + " às "
                + xepa.getHorarioRetiradaFim().format(HORA);
        List<Integer> dias = new ArrayList<>(feira.getDias());
        Collections.sort(dias);
        return new XepaResponse(xepa.getId(), produto.getNome(), Categorias.paraApp(produto.getCategoria().getNome()),
                partes[0], partes[1], produto.getImagem(), produto.getUnidadeMedida(), xepa.getPreco(),
                produto.getPrecoReferencia(), xepa.getQuantidade(), xepa.getValidade(), xepa.getDataRetirada(),
                xepa.getHorarioRetiradaInicio(), xepa.getHorarioRetiradaFim(), horario, xepa.getStatus(),
                feira.getId(), feira.getNome(), localDaFeira(feira), feira.getCep(),
                dias, feira.getLatitude(), feira.getLongitude(),
                feirante.getConta().getId(), feirante.getConta().getNome(), feirante.getNomeFantasia(),
                xepa.getDataCadastro());
    }

    // Endereço e número juntos, ex.: "Rua X, 10"
    public static String localDaFeira(Feira feira) {
        String endereco = feira.getEndereco();
        String numero = feira.getNumero();
        boolean temEndereco = endereco != null && !endereco.isBlank();
        boolean temNumero = numero != null && !numero.isBlank();
        if (temEndereco && temNumero) {
            return endereco + ", " + numero;
        } else if (temEndereco) {
            return endereco;
        } else if (temNumero) {
            return numero;
        } else {
            return "";
        }
    }

    private Categoria categoria(String nome) {
        Categoria categoria = categoriaRepository.findByNomeIgnoreCase(nome).orElse(null);
        if (categoria == null) {
            categoria = new Categoria();
            categoria.setNome(nome);
            categoria = categoriaRepository.save(categoria);
        }
        return categoria;
    }

    private static String juntarDescricao(String itens, String descricao) {
        String a = "";
        if (itens != null) {
            a = itens.trim();
        }
        String b = "";
        if (descricao != null) {
            b = descricao.trim();
        }
        if (a.isEmpty() && b.isEmpty()) {
            return null;
        }
        return a + SEPARADOR + b;
    }

    private static String[] separarDescricao(String texto) {
        if (texto == null) {
            return new String[]{"", ""};
        }
        int i = texto.indexOf(SEPARADOR);
        if (i < 0) {
            return new String[]{"", texto};
        }
        return new String[]{texto.substring(0, i), texto.substring(i + SEPARADOR.length())};
    }

    private Feirante feirante(Integer idConta) {
        Feirante feirante = feiranteRepository.findByConta_Id(idConta).orElse(null);
        if (feirante == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Feirante não encontrado.");
        }
        return feirante;
    }

    private static BigDecimal valor(BigDecimal valor, String mensagem) {
        if (valor == null || valor.signum() <= 0 || valor.compareTo(VALOR_MAXIMO) > 0) {
            throw invalido(mensagem);
        }
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    private static String texto(String valor, String mensagem, int maximo) {
        if (valor == null || valor.isBlank()) {
            throw invalido(mensagem);
        }
        String limpo = valor.trim();
        if (limpo.length() > maximo) {
            throw invalido("Texto muito longo (máximo " + maximo + " caracteres).");
        }
        return limpo;
    }

    static String semAcento(String texto) {
        if (texto == null) {
            return "";
        }
        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.toLowerCase(Locale.ROOT).trim();
    }

    private static ResponseStatusException invalido(String mensagem) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
    }
}
