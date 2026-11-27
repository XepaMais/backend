package br.com.xepamais.api.receita;

import br.com.xepamais.api.database.model.IngredienteReceita;
import br.com.xepamais.api.database.model.ReceitaCulinaria;
import br.com.xepamais.api.database.repository.IngredienteReceitaRepository;
import br.com.xepamais.api.database.repository.ReceitaCulinariaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Receitas da Cozinha (públicas). Novas receitas podem ser cadastradas pelo painel admin (tabelas do banco).
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

    // ingredientes: só os nomes (usados nos filtros da Cozinha); itens: nome + quantidade.
    public record ReceitaResponse(Integer id, String codigo, String nome, String categoria, Integer tempoMinutos,
                                  String dificuldade, String imagem, String modoPreparo, List<String> ingredientes,
                                  List<IngredienteResponse> itens) {}

    // GET /api/receitas (todas) ou /api/receitas?categoria=Sopas
    @GetMapping
    @Transactional(readOnly = true)
    public List<ReceitaResponse> listar(@RequestParam(required = false) String categoria) {
        Map<Integer, List<IngredienteReceita>> ingredientes = ingredienteRepository.findAllByOrderByIdAsc().stream()
                .collect(Collectors.groupingBy(i -> i.getReceita().getId()));
        return receitaRepository.findAllByOrderByIdAsc().stream()
                .filter(r -> categoria == null || categoria.isBlank() || categoria.equalsIgnoreCase(r.getCategoria()))
                .map(r -> resposta(r, ingredientes.getOrDefault(r.getId(), List.of())))
                .toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ReceitaResponse detalhe(@PathVariable Integer id) {
        ReceitaCulinaria receita = receitaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Receita não encontrada."));
        return resposta(receita, ingredienteRepository.findByReceita_IdOrderByIdAsc(id));
    }

    private static ReceitaResponse resposta(ReceitaCulinaria r, List<IngredienteReceita> itens) {
        return new ReceitaResponse(r.getId(), r.getCodigo(), r.getTitulo(), r.getCategoria(), r.getTempoPreparo(),
                r.getDificuldade(), r.getImagem(), r.getModoPreparo(),
                itens.stream().map(IngredienteReceita::getNome).toList(),
                itens.stream().map(i -> new IngredienteResponse(i.getNome(), i.getQuantidade())).toList());
    }
}
