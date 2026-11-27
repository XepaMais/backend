package br.com.xepamais.api.receita;

import br.com.xepamais.api.database.model.IngredienteReceita;
import br.com.xepamais.api.database.model.ReceitaCulinaria;
import br.com.xepamais.api.database.repository.IngredienteReceitaRepository;
import br.com.xepamais.api.database.repository.ReceitaCulinariaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Receitas da Cozinha
@RestController
@io.swagger.v3.oas.annotations.tags.Tag(name = "Receitas", description = "Receitas para aproveitar a xepa")
@RequestMapping("/api/receitas")
public class ReceitaController {
    private final ReceitaCulinariaRepository receitaRepository;
    private final IngredienteReceitaRepository ingredienteRepository;

    public ReceitaController(ReceitaCulinariaRepository receitaRepository,
                             IngredienteReceitaRepository ingredienteRepository) {
        this.receitaRepository = receitaRepository;
        this.ingredienteRepository = ingredienteRepository;
    }

    public record IngredienteResponse(String nome, String quantidade) {}

    // ingredientes: só os nomes; itens: nome + quantidade
    public record ReceitaResponse(Integer id, String codigo, String nome, String categoria, Integer tempoMinutos,
                                  String dificuldade, String imagem, String modoPreparo, List<String> ingredientes,
                                  List<IngredienteResponse> itens) {}

    // GET /api/receitas ou /api/receitas?categoria=Sopas
    @GetMapping
    @Transactional(readOnly = true)
    public List<ReceitaResponse> listar(@RequestParam(required = false) String categoria) {
        // Separa os ingredientes por receita
        Map<Integer, List<IngredienteReceita>> ingredientes = new HashMap<>();
        for (IngredienteReceita i : ingredienteRepository.findAllByOrderByIdAsc()) {
            Integer idReceita = i.getReceita().getId();
            if (ingredientes.get(idReceita) == null) {
                ingredientes.put(idReceita, new ArrayList<>());
            }
            ingredientes.get(idReceita).add(i);
        }

        List<ReceitaResponse> lista = new ArrayList<>();
        for (ReceitaCulinaria r : receitaRepository.findAllByOrderByIdAsc()) {
            if (categoria != null && !categoria.isBlank() && !categoria.equalsIgnoreCase(r.getCategoria())) {
                continue;
            }
            List<IngredienteReceita> daReceita = ingredientes.get(r.getId());
            if (daReceita == null) {
                daReceita = new ArrayList<>();
            }
            lista.add(resposta(r, daReceita));
        }
        return lista;
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ReceitaResponse detalhe(@PathVariable Integer id) {
        ReceitaCulinaria receita = receitaRepository.findById(id).orElse(null);
        if (receita == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Receita não encontrada.");
        }
        return resposta(receita, ingredienteRepository.findByReceita_IdOrderByIdAsc(id));
    }

    private static ReceitaResponse resposta(ReceitaCulinaria r, List<IngredienteReceita> itens) {
        List<String> nomes = new ArrayList<>();
        List<IngredienteResponse> itensResposta = new ArrayList<>();
        for (IngredienteReceita i : itens) {
            nomes.add(i.getNome());
            itensResposta.add(new IngredienteResponse(i.getNome(), i.getQuantidade()));
        }
        return new ReceitaResponse(r.getId(), r.getCodigo(), r.getTitulo(), r.getCategoria(), r.getTempoPreparo(),
                r.getDificuldade(), r.getImagem(), r.getModoPreparo(), nomes, itensResposta);
    }
}
