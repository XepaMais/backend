package br.com.xepamais.api.receita;

import br.com.xepamais.api.database.model.IngredienteReceita;
import br.com.xepamais.api.database.model.ReceitaCulinaria;
import br.com.xepamais.api.database.repository.IngredienteReceitaRepository;
import br.com.xepamais.api.database.repository.ReceitaCulinariaRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Garante no banco as receitas que a Cozinha já mostrava (as mesmas do front).
// Roda depois da AtualizacaoBanco. Receita que já existe (pelo código) não é alterada.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ReceitasIniciais implements ApplicationRunner {

    private record Padrao(String codigo, String nome, String categoria, int minutos, String dificuldade,
                          List<String> ingredientes) {}

    private static final List<Padrao> RECEITAS = List.of(
            new Padrao("salada-caesar", "Salada caesar", "Saladas", 15, "Fácil", List.of("tomate", "cenoura", "alface")),
            new Padrao("caldo-verde", "Caldo verde", "Sopas", 35, "Fácil", List.of("couve", "batata", "cebola")),
            new Padrao("bolo-banana", "Bolo de banana", "Doces", 45, "Média", List.of("banana", "aveia", "canela")),
            new Padrao("talos-salteados", "Talos salteados com alho", "Aproveitamento integral", 20, "Fácil",
                    List.of("couve", "alho", "cenoura")),
            new Padrao("tomates-recheados", "Tomates recheados", "Rápidas", 25, "Média",
                    List.of("tomate", "arroz", "queijo")),
            new Padrao("creme-cenoura", "Creme de cenoura", "Sopas", 30, "Fácil", List.of("cenoura", "batata", "gengibre")),
            new Padrao("salada-frutas", "Salada de frutas da estação", "Doces", 12, "Fácil",
                    List.of("banana", "mamão", "laranja")),
            new Padrao("refogado-colorido", "Refogado colorido", "Aproveitamento integral", 18, "Fácil",
                    List.of("cenoura", "couve", "cebola")),
            new Padrao("vitamina-banana-mamao", "Vitamina de banana e mamão", "Sucos", 5, "Fácil",
                    List.of("banana", "mamão", "leite")));

    private final ReceitaCulinariaRepository receitaRepository;
    private final IngredienteReceitaRepository ingredienteRepository;

    public ReceitasIniciais(ReceitaCulinariaRepository receitaRepository,
                            IngredienteReceitaRepository ingredienteRepository) {
        this.receitaRepository = receitaRepository;
        this.ingredienteRepository = ingredienteRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (Padrao padrao : RECEITAS) {
            if (receitaRepository.findByCodigo(padrao.codigo()).isPresent()) continue;
            ReceitaCulinaria receita = new ReceitaCulinaria();
            receita.setCodigo(padrao.codigo());
            receita.setTitulo(padrao.nome());
            receita.setCategoria(padrao.categoria());
            receita.setTempoPreparo(padrao.minutos());
            receita.setDificuldade(padrao.dificuldade());
            receitaRepository.save(receita);
            for (String nome : padrao.ingredientes()) {
                IngredienteReceita ingrediente = new IngredienteReceita();
                ingrediente.setReceita(receita);
                ingrediente.setNome(nome);
                ingredienteRepository.save(ingrediente);
            }
        }
    }
}
