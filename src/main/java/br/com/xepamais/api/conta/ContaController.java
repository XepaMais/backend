package br.com.xepamais.api.conta;

import br.com.xepamais.api.auth.PasswordHasher;
import br.com.xepamais.api.database.model.Conta;
import br.com.xepamais.api.database.model.Feirante;
import br.com.xepamais.api.database.repository.ConsumidorRepository;
import br.com.xepamais.api.database.repository.ContaRepository;
import br.com.xepamais.api.database.repository.FeiranteRepository;
import br.com.xepamais.api.imagem.ImagemController;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Pattern;

// Perfil da conta logada (consumidor ou feirante): tudo fica no banco, vale em qualquer aparelho.
// {idConta} é o "id" do login; a rota exige o token da mesma conta (ContaInterceptor).
@RestController
@io.swagger.v3.oas.annotations.tags.Tag(name = "Conta", description = "Perfil e troca de senha")
@RequestMapping("/api/conta/{idConta}")
public class ContaController {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final ContaRepository contaRepository;
    private final FeiranteRepository feiranteRepository;
    private final ConsumidorRepository consumidorRepository;
    private final PasswordHasher passwordHasher;

    public ContaController(ContaRepository contaRepository, FeiranteRepository feiranteRepository,
                           ConsumidorRepository consumidorRepository, PasswordHasher passwordHasher) {
        this.contaRepository = contaRepository;
        this.feiranteRepository = feiranteRepository;
        this.consumidorRepository = consumidorRepository;
        this.passwordHasher = passwordHasher;
    }

    // Campos do feirante vêm nulos para consumidor. barraca = nome fantasia; grupo = grupo de comércio.
    public record PerfilResponse(Integer id, String tipo, String nome, String email, String cpf, String telefone,
                                 LocalDate nascimento, String foto, LocalDateTime dataCadastro,
                                 String barraca, String cnpj, String ccm, String matricula, String validade,
                                 String titular, String grupo) {}

    // Campo nulo = fica como está. Texto vazio = apaga (onde o banco permite). foto: "" remove a foto.
    public record PerfilRequest(String nome, String email, String cpf, String telefone, LocalDate nascimento,
                                String foto, String barraca, String cnpj, String ccm, String matricula,
                                String validade, String titular, String grupo) {}

    public record SenhaRequest(String senhaAtual, String novaSenha) {}

    @GetMapping("/perfil")
    @Transactional(readOnly = true)
    public PerfilResponse perfil(@PathVariable Integer idConta) {
        return resposta(conta(idConta));
    }

    @PutMapping("/perfil")
    @Transactional
    public PerfilResponse atualizar(@PathVariable Integer idConta, @RequestBody PerfilRequest request) {
        Conta conta = conta(idConta);
        if (request == null) throw invalido("Informe os dados do perfil.");

        if (request.nome() != null) conta.setNome(obrigatorio(request.nome(), "Informe o nome.", 100));
        if (request.telefone() != null) conta.setTelefone(opcional(request.telefone(), 20));
        if (request.nascimento() != null) {
            if (request.nascimento().isAfter(LocalDate.now())) throw invalido("Data de nascimento inválida.");
            conta.setDataNascimento(request.nascimento());
        }
        if (request.email() != null) {
            String email = request.email().trim().toLowerCase(Locale.ROOT);
            if (!EMAIL.matcher(email).matches() || email.length() > 100) throw invalido("Informe um email válido.");
            if (!email.equalsIgnoreCase(conta.getEmail()) && contaRepository.existsByEmailIgnoreCase(email)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Este email já está cadastrado.");
            }
            conta.setEmail(email);
        }
        if (request.cpf() != null && !request.cpf().isBlank()) {
            String cpf = request.cpf().replaceAll("\\D", "");
            if (cpf.length() != 11) throw invalido("O CPF deve conter 11 dígitos.");
            if (!cpf.equals(conta.getCpf()) && contaRepository.existsByCpf(cpf)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Este CPF já está cadastrado.");
            }
            conta.setCpf(cpf);
        }
        if (request.foto() != null) {
            String foto = ImagemController.enderecoValido(request.foto());
            if (foto == null && !request.foto().isBlank()) throw invalido("Envie a foto antes em /api/imagens.");
            conta.setFoto(foto);
        }

        feiranteRepository.findByConta_Id(idConta).ifPresent(feirante -> atualizarFeirante(feirante, request));
        return resposta(conta);
    }

    @PutMapping("/senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void trocarSenha(@PathVariable Integer idConta, @RequestBody SenhaRequest request) {
        Conta conta = conta(idConta);
        if (request == null || request.senhaAtual() == null || request.novaSenha() == null) {
            throw invalido("Informe a senha atual e a nova senha.");
        }
        if (!passwordHasher.matches(request.senhaAtual(), conta.getSenha())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A senha atual está incorreta.");
        }
        if (request.novaSenha().length() < 8) throw invalido("A nova senha deve ter pelo menos 8 caracteres.");
        conta.setSenha(passwordHasher.hash(request.novaSenha()));
    }

    private void atualizarFeirante(Feirante feirante, PerfilRequest request) {
        if (request.barraca() != null) {
            feirante.setNomeFantasia(obrigatorio(request.barraca(), "Informe o nome fantasia.", 100));
        }
        if (request.cnpj() != null) {
            String cnpj = request.cnpj().replaceAll("\\D", "");
            if (cnpj.isEmpty()) {
                feirante.setCnpj(null);
            } else {
                if (cnpj.length() != 14) throw invalido("O CNPJ deve conter 14 dígitos.");
                if (!cnpj.equals(feirante.getCnpj()) && feiranteRepository.existsByCnpj(cnpj)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Este CNPJ já está cadastrado.");
                }
                feirante.setCnpj(cnpj);
            }
        }
        if (request.matricula() != null) {
            String matricula = opcional(request.matricula(), 20);
            if (matricula != null && !matricula.equals(feirante.getMatricula())
                    && feiranteRepository.existsByMatricula(matricula)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta matrícula já está cadastrada.");
            }
            feirante.setMatricula(matricula);
        }
        aplicar(request.ccm(), 20, feirante::setCcm);
        aplicar(request.validade(), 20, feirante::setValidadeMatricula);
        aplicar(request.titular(), 100, feirante::setTitular);
        aplicar(request.grupo(), 60, feirante::setGrupoComercio);
    }

    private PerfilResponse resposta(Conta conta) {
        Feirante feirante = feiranteRepository.findByConta_Id(conta.getId()).orElse(null);
        String tipo = feirante != null ? "FEIRANTE"
                : consumidorRepository.existsByConta_Id(conta.getId()) ? "CONSUMIDOR" : "CONTA";
        return new PerfilResponse(conta.getId(), tipo, conta.getNome(), conta.getEmail(), conta.getCpf(),
                conta.getTelefone(), conta.getDataNascimento(), conta.getFoto(), conta.getDataCadastro(),
                feirante == null ? null : feirante.getNomeFantasia(),
                feirante == null ? null : feirante.getCnpj(),
                feirante == null ? null : feirante.getCcm(),
                feirante == null ? null : feirante.getMatricula(),
                feirante == null ? null : feirante.getValidadeMatricula(),
                feirante == null ? null : feirante.getTitular(),
                feirante == null ? null : feirante.getGrupoComercio());
    }

    private Conta conta(Integer idConta) {
        return contaRepository.findById(idConta).filter(Conta::isAtivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conta não encontrada."));
    }

    private static void aplicar(String valor, int maximo, Consumer<String> definir) {
        if (valor != null) definir.accept(opcional(valor, maximo));
    }

    private static String obrigatorio(String valor, String mensagem, int maximo) {
        String limpo = opcional(valor, maximo);
        if (limpo == null) throw invalido(mensagem);
        return limpo;
    }

    private static String opcional(String valor, int maximo) {
        String limpo = valor.trim();
        if (limpo.length() > maximo) throw invalido("Texto muito longo (máximo " + maximo + " caracteres).");
        return limpo.isEmpty() ? null : limpo;
    }

    private static ResponseStatusException invalido(String mensagem) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
    }
}
