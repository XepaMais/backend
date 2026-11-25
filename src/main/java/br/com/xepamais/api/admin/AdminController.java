package br.com.xepamais.api.admin;

import br.com.xepamais.api.auth.PasswordHasher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Painel de administração do banco. O front só mostra a tela; quem decide o acesso é aqui.
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private static final int TENTATIVAS_MAXIMAS = 10;
    private static final long BLOQUEIO_SEGUNDOS = 15 * 60;

    private final String emailAdmin;
    private final String senhaHash;
    private final PasswordHasher hasher;
    private final AdminTokenService tokens;
    private final AdminBancoService banco;
    private final AdminUsuariosService usuarios;
    private final Map<String, long[]> falhas = new ConcurrentHashMap<>();

    public AdminController(@Value("${app.admin.email}") String emailAdmin,
                           @Value("${app.admin.senha-hash}") String senhaHash,
                           PasswordHasher hasher, AdminTokenService tokens, AdminBancoService banco,
                           AdminUsuariosService usuarios) {
        this.emailAdmin = emailAdmin.trim().toLowerCase(Locale.ROOT);
        this.senhaHash = senhaHash;
        this.hasher = hasher;
        this.tokens = tokens;
        this.banco = banco;
        this.usuarios = usuarios;
    }

    public record LoginAdmin(String email, String senha) {}

    @PostMapping("/login")
    public Map<String, String> login(@RequestBody LoginAdmin dados) {
        // Contador único (não por IP): o cabeçalho X-Forwarded-For pode ser forjado.
        String origem = "global";
        long agora = Instant.now().getEpochSecond();
        long[] registro = falhas.get(origem);
        if (registro != null && registro[0] >= TENTATIVAS_MAXIMAS && agora - registro[1] < BLOQUEIO_SEGUNDOS) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Muitas tentativas. Aguarde 15 minutos e tente de novo.");
        }

        String email = dados.email() == null ? "" : dados.email().trim().toLowerCase(Locale.ROOT);
        boolean ok = email.equals(emailAdmin) && dados.senha() != null && hasher.matches(dados.senha(), senhaHash);
        if (!ok) {
            falhas.compute(origem, (k, v) -> v == null || agora - v[1] >= BLOQUEIO_SEGUNDOS
                    ? new long[]{1, agora} : new long[]{v[0] + 1, agora});
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email ou senha inválidos.");
        }
        falhas.remove(origem);
        return Map.of("token", tokens.gerar(email), "email", email);
    }

    @GetMapping("/usuarios")
    public AdminUsuariosService.Usuarios usuarios() {
        return usuarios.listar();
    }

    @GetMapping("/tabelas")
    public List<AdminBancoService.Tabela> tabelas() {
        return banco.tabelas();
    }

    @GetMapping("/tabelas/{tabela}")
    public AdminBancoService.Pagina linhas(@PathVariable String tabela,
                                           @RequestParam(defaultValue = "0") int pagina,
                                           @RequestParam(defaultValue = "25") int tamanho,
                                           @RequestParam(required = false) String busca) {
        return banco.linhas(tabela, pagina, tamanho, busca);
    }

    @PostMapping("/tabelas/{tabela}")
    @ResponseStatus(HttpStatus.CREATED)
    public void inserir(@PathVariable String tabela, @RequestBody Map<String, Object> valores) {
        banco.inserir(tabela, valores);
    }

    @PutMapping("/tabelas/{tabela}/{id}")
    public void atualizar(@PathVariable String tabela, @PathVariable String id,
                          @RequestBody Map<String, Object> valores) {
        banco.atualizar(tabela, id, valores);
    }

    @DeleteMapping("/tabelas/{tabela}/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable String tabela, @PathVariable String id) {
        banco.excluir(tabela, id);
    }
}
