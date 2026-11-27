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

// Carrinho do consumidor. {idConta} é o "id" devolvido pelo login.
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
        return consumidorRepository.findByConta_Id(idConta)
                .filter(c -> c.getConta().isAtivo())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Consumidor não encontrado."));
    }

    // ---------- Carrinho ----------

    @Transactional
    public CarrinhoResponse carrinho(Integer idConta) {
        return montar(carrinhoDo(consumidor(idConta)));
    }

    // Coloca a xepa no carrinho ou troca a quantidade dela.
    @Transactional
    public CarrinhoResponse definirItem(Integer idConta, Integer idXepa, BigDecimal quantidade) {
        Carrinho carrinho = carrinhoDo(consumidor(idConta));
        Xepa xepa = xepaVendavel(idXepa);
        BigDecimal qtd = quantidade(quantidade).min(xepa.getQuantidade());
        ItemCarrinho item = itemCarrinhoRepository.findByCarrinho_IdAndXepa_Id(carrinho.getId(), idXepa)
                .orElseGet(() -> {
                    ItemCarrinho novo = new ItemCarrinho();
                    novo.setCarrinho(carrinho);
                    novo.setXepa(xepa);
                    return novo;
                });
        item.setQuantidade(qtd);
        itemCarrinhoRepository.save(item);
        return montar(carrinho);
    }

    @Transactional
    public CarrinhoResponse removerItem(Integer idConta, Integer idXepa) {
        Carrinho carrinho = carrinhoDo(consumidor(idConta));
        itemCarrinhoRepository.findByCarrinho_IdAndXepa_Id(carrinho.getId(), idXepa)
                .ifPresent(itemCarrinhoRepository::delete);
        return montar(carrinho);
    }

    @Transactional
    public CarrinhoResponse substituir(Integer idConta, CarrinhoRequest request) {
        Carrinho carrinho = carrinhoDo(consumidor(idConta));
        itemCarrinhoRepository.esvaziar(carrinho.getId());
        itemCarrinhoRepository.flush();
        Map<Integer, BigDecimal> itens = agrupar(request == null ? null : request.itens());
        for (Map.Entry<Integer, BigDecimal> entrada : itens.entrySet()) {
            xepaRepository.findById(entrada.getKey()).filter(XepaService::disponivel).ifPresent(xepa -> {
                ItemCarrinho item = new ItemCarrinho();
                item.setCarrinho(carrinho);
                item.setXepa(xepa);
                item.setQuantidade(entrada.getValue().min(xepa.getQuantidade()));
                itemCarrinhoRepository.save(item);
            });
        }
        return montar(carrinho);
    }

    @Transactional
    public void esvaziar(Integer idConta) {
        Carrinho carrinho = carrinhoDo(consumidor(idConta));
        itemCarrinhoRepository.esvaziar(carrinho.getId());
    }

    public Carrinho carrinhoDo(Consumidor consumidor) {
        return carrinhoRepository.findByConsumidor_Id(consumidor.getId()).orElseGet(() -> {
            Carrinho novo = new Carrinho();
            novo.setConsumidor(consumidor);
            return carrinhoRepository.save(novo);
        });
    }

    // Itens do carrinho salvo, como pares (id da xepa -> quantidade).
    public Map<Integer, BigDecimal> itensSalvos(Carrinho carrinho) {
        Map<Integer, BigDecimal> itens = new LinkedHashMap<>();
        for (ItemCarrinho item : itemCarrinhoRepository.findByCarrinho_IdOrderByIdAsc(carrinho.getId())) {
            itens.put(item.getXepa().getId(), item.getQuantidade());
        }
        return itens;
    }

    // Junta itens repetidos e valida as quantidades.
    public Map<Integer, BigDecimal> agrupar(List<ItemCarrinhoRequest> itens) {
        Map<Integer, BigDecimal> agrupados = new LinkedHashMap<>();
        if (itens == null) return agrupados;
        for (ItemCarrinhoRequest item : itens) {
            if (item == null || item.idXepa() == null) throw invalido("Item do carrinho sem xepa.");
            agrupados.merge(item.idXepa(), quantidade(item.quantidade()), BigDecimal::add);
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
                itemCarrinhoRepository.delete(item); // esgotou, venceu ou saiu do ar
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
        Xepa xepa = xepaRepository.findById(idXepa)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Xepa não encontrada."));
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
