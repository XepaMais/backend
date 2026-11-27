package br.com.xepamais.api.consumidor;

import br.com.xepamais.api.consumidor.dto.ConsumidorDtos.*;
import br.com.xepamais.api.database.model.*;
import br.com.xepamais.api.database.repository.*;
import br.com.xepamais.api.xepa.XepaService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Carrinho do consumidor. {idConta} é o "id" devolvido pelo login
@Service
public class ConsumidorService {
    private static final BigDecimal QUANTIDADE_MAXIMA = new BigDecimal("9999");

    private final ConsumidorRepository consumidorRepository;
    private final CarrinhoRepository carrinhoRepository;
    private final ItemCarrinhoRepository itemCarrinhoRepository;
    private final XepaRepository xepaRepository;

    public ConsumidorService(ConsumidorRepository consumidorRepository, CarrinhoRepository carrinhoRepository,
                             ItemCarrinhoRepository itemCarrinhoRepository, XepaRepository xepaRepository) {
        this.consumidorRepository = consumidorRepository;
        this.carrinhoRepository = carrinhoRepository;
        this.itemCarrinhoRepository = itemCarrinhoRepository;
        this.xepaRepository = xepaRepository;
    }

    public Consumidor consumidor(Integer idConta) {
        Consumidor consumidor = consumidorRepository.findByConta_Id(idConta).orElse(null);
        if (consumidor == null || !consumidor.getConta().isAtivo()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Consumidor não encontrado.");
        }
        return consumidor;
    }

    // Carrinho

    @Transactional
    public CarrinhoResponse carrinho(Integer idConta) {
        return montar(carrinhoDo(consumidor(idConta)));
    }

    // Coloca a xepa no carrinho ou troca a quantidade dela
    @Transactional
    public CarrinhoResponse definirItem(Integer idConta, Integer idXepa, BigDecimal quantidade) {
        Carrinho carrinho = carrinhoDo(consumidor(idConta));
        Xepa xepa = xepaVendavel(idXepa);
        BigDecimal qtd = quantidade(quantidade).min(xepa.getQuantidade());
        ItemCarrinho item = itemCarrinhoRepository.findByCarrinho_IdAndXepa_Id(carrinho.getId(), idXepa).orElse(null);
        if (item == null) {
            item = new ItemCarrinho();
            item.setCarrinho(carrinho);
            item.setXepa(xepa);
        }
        item.setQuantidade(qtd);
        itemCarrinhoRepository.save(item);
        return montar(carrinho);
    }

    @Transactional
    public CarrinhoResponse removerItem(Integer idConta, Integer idXepa) {
        Carrinho carrinho = carrinhoDo(consumidor(idConta));
        ItemCarrinho item = itemCarrinhoRepository.findByCarrinho_IdAndXepa_Id(carrinho.getId(), idXepa).orElse(null);
        if (item != null) {
            itemCarrinhoRepository.delete(item);
        }
        return montar(carrinho);
    }

    @Transactional
    public CarrinhoResponse substituir(Integer idConta, CarrinhoRequest request) {
        Carrinho carrinho = carrinhoDo(consumidor(idConta));
        itemCarrinhoRepository.esvaziar(carrinho.getId());
        itemCarrinhoRepository.flush();
        List<ItemCarrinhoRequest> recebidos = null;
        if (request != null) {
            recebidos = request.itens();
        }
        Map<Integer, BigDecimal> itens = agrupar(recebidos);
        for (Integer idXepa : itens.keySet()) {
            Xepa xepa = xepaRepository.findById(idXepa).orElse(null);
            // Xepa que não existe mais ou não está à venda fica de fora
            if (xepa == null || !XepaService.disponivel(xepa)) {
                continue;
            }
            ItemCarrinho item = new ItemCarrinho();
            item.setCarrinho(carrinho);
            item.setXepa(xepa);
            item.setQuantidade(itens.get(idXepa).min(xepa.getQuantidade()));
            itemCarrinhoRepository.save(item);
        }
        return montar(carrinho);
    }

    @Transactional
    public void esvaziar(Integer idConta) {
        Carrinho carrinho = carrinhoDo(consumidor(idConta));
        itemCarrinhoRepository.esvaziar(carrinho.getId());
    }

    // Endereço de entrega

    @Transactional(readOnly = true)
    public RecebimentoPreferidoResponse recebimento(Integer idConta) {
        return recebimento(consumidor(idConta));
    }

    @Transactional
    public RecebimentoPreferidoResponse salvarRecebimento(Integer idConta, RecebimentoPreferidoRequest request) {
        Consumidor consumidor = consumidor(idConta);
        if (request == null) {
            throw invalido("Informe a forma de receber.");
        }
        if (request.tipo() != null) {
            String tipo = request.tipo().trim().toLowerCase(java.util.Locale.ROOT);
            if (tipo.equals("entrega")) {
                consumidor.setRecebimentoPreferido("ENTREGA");
            } else if (tipo.equals("retirada")) {
                consumidor.setRecebimentoPreferido("RETIRADA");
            } else {
                throw invalido("Forma de receber inválida.");
            }
        }
        if (request.endereco() != null) {
            guardarEndereco(consumidor, request.endereco());
        }
        return recebimento(consumidor);
    }

    // Também usado no checkout com entrega: o endereço usado fica salvo para a próxima compra
    public void guardarEndereco(Consumidor consumidor, EnderecoRequest e) {
        String cep = "";
        if (e.cep() != null) {
            cep = e.cep().replaceAll("\\D", "");
        }
        if (cep.length() > 8) {
            throw invalido("O CEP precisa ter 8 números.");
        }
        String uf = texto(e.uf(), 2, "UF");
        if (uf != null && !uf.matches("[A-Za-z]{2}")) {
            throw invalido("UF inválida.");
        }
        if (cep.isEmpty()) {
            consumidor.setCepEntrega(null);
        } else {
            consumidor.setCepEntrega(cep);
        }
        consumidor.setLogradouroEntrega(texto(e.logradouro(), 150, "rua"));
        consumidor.setNumeroEntrega(texto(e.numero(), 10, "número"));
        consumidor.setComplementoEntrega(texto(e.complemento(), 60, "complemento"));
        consumidor.setBairroEntrega(texto(e.bairro(), 80, "bairro"));
        consumidor.setCidadeEntrega(texto(e.cidade(), 80, "cidade"));
        if (uf == null) {
            consumidor.setUfEntrega(null);
        } else {
            consumidor.setUfEntrega(uf.toUpperCase(java.util.Locale.ROOT));
        }
    }

    private RecebimentoPreferidoResponse recebimento(Consumidor c) {
        String tipo = "retirada";
        if ("ENTREGA".equals(c.getRecebimentoPreferido())) {
            tipo = "entrega";
        }
        return new RecebimentoPreferidoResponse(tipo,
                new EnderecoResponse(vazio(c.getCepEntrega()), vazio(c.getLogradouroEntrega()),
                        vazio(c.getNumeroEntrega()), vazio(c.getComplementoEntrega()), vazio(c.getBairroEntrega()),
                        vazio(c.getCidadeEntrega()), vazio(c.getUfEntrega())));
    }

    private static String texto(String valor, int maximo, String campo) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String limpo = valor.trim();
        if (limpo.length() > maximo) {
            throw invalido("Confira o campo " + campo + " (máximo " + maximo + " caracteres).");
        }
        return limpo;
    }

    private static String vazio(String valor) {
        if (valor == null) {
            return "";
        }
        return valor;
    }

    public Carrinho carrinhoDo(Consumidor consumidor) {
        Carrinho carrinho = carrinhoRepository.findByConsumidor_Id(consumidor.getId()).orElse(null);
        if (carrinho == null) {
            carrinho = new Carrinho();
            carrinho.setConsumidor(consumidor);
            carrinho = carrinhoRepository.save(carrinho);
        }
        return carrinho;
    }

    // Itens do carrinho salvo, como pares
    public Map<Integer, BigDecimal> itensSalvos(Carrinho carrinho) {
        Map<Integer, BigDecimal> itens = new LinkedHashMap<>();
        for (ItemCarrinho item : itemCarrinhoRepository.findByCarrinho_IdOrderByIdAsc(carrinho.getId())) {
            itens.put(item.getXepa().getId(), item.getQuantidade());
        }
        return itens;
    }

    // Junta itens repetidos e valida as quantidades
    public Map<Integer, BigDecimal> agrupar(List<ItemCarrinhoRequest> itens) {
        Map<Integer, BigDecimal> agrupados = new LinkedHashMap<>();
        if (itens == null) {
            return agrupados;
        }
        for (ItemCarrinhoRequest item : itens) {
            if (item == null || item.idXepa() == null) {
                throw invalido("Item do carrinho sem xepa.");
            }
            BigDecimal qtd = quantidade(item.quantidade());
            if (agrupados.containsKey(item.idXepa())) {
                // Xepa repetida: soma as quantidades
                agrupados.put(item.idXepa(), agrupados.get(item.idXepa()).add(qtd));
            } else {
                agrupados.put(item.idXepa(), qtd);
            }
        }
        return agrupados;
    }

    private CarrinhoResponse montar(Carrinho carrinho) {
        List<ItemCarrinhoResponse> itens = new ArrayList<>();
        BigDecimal totalItens = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (ItemCarrinho item : itemCarrinhoRepository.findByCarrinho_IdOrderByIdAsc(carrinho.getId())) {
            Xepa xepa = item.getXepa();
            if (!XepaService.disponivel(xepa)) {
                // Esgotou, venceu ou saiu do ar
                itemCarrinhoRepository.delete(item);
                continue;
            }
            BigDecimal qtd = item.getQuantidade().min(xepa.getQuantidade());
            itens.add(new ItemCarrinhoResponse(xepa.getId(), qtd, xepa.getQuantidade(), XepaService.paraResposta(xepa)));
            totalItens = totalItens.add(qtd);
            total = total.add(xepa.getPreco().multiply(qtd));
        }
        return new CarrinhoResponse(itens, totalItens, total.setScale(2, RoundingMode.HALF_UP));
    }

    private Xepa xepaVendavel(Integer idXepa) {
        Xepa xepa = xepaRepository.findById(idXepa).orElse(null);
        if (xepa == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Xepa não encontrada.");
        }
        if (!XepaService.disponivel(xepa)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Essa xepa não está mais disponível.");
        }
        return xepa;
    }

    private static BigDecimal quantidade(BigDecimal quantidade) {
        if (quantidade == null || quantidade.signum() <= 0 || quantidade.compareTo(QUANTIDADE_MAXIMA) > 0) {
            throw invalido("Informe uma quantidade maior que zero.");
        }
        return quantidade.setScale(2, RoundingMode.HALF_UP);
    }

    static ResponseStatusException invalido(String mensagem) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
    }
}
