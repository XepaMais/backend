package br.com.xepamais.api.auth;

import br.com.xepamais.api.auth.dto.CadastroConsumidorRequest;
import br.com.xepamais.api.auth.dto.CadastroFeiranteRequest;
import br.com.xepamais.api.auth.dto.ContaResponse;
import br.com.xepamais.api.auth.dto.LoginRequest;
import br.com.xepamais.api.database.model.Conta;
import br.com.xepamais.api.database.model.Consumidor;
import br.com.xepamais.api.database.model.Feirante;
import br.com.xepamais.api.database.repository.ContaRepository;
import br.com.xepamais.api.database.repository.ConsumidorRepository;
import br.com.xepamais.api.database.repository.FeiranteRepository;
import br.com.xepamais.api.plano.PlanoService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@Service
public class AuthService {
    private final ContaRepository contaRepository;
    private final ConsumidorRepository consumidorRepository;
    private final FeiranteRepository feiranteRepository;
    private final PasswordHasher passwordHasher;
    private final SessaoService sessaoService;

    public AuthService(ContaRepository contaRepository, ConsumidorRepository consumidorRepository,
                       FeiranteRepository feiranteRepository,
                       PasswordHasher passwordHasher, SessaoService sessaoService) {
        this.contaRepository = contaRepository;
        this.consumidorRepository = consumidorRepository;
        this.feiranteRepository = feiranteRepository;
        this.passwordHasher = passwordHasher;
        this.sessaoService = sessaoService;
    }

    @Transactional
    public ContaResponse cadastrarConsumidor(CadastroConsumidorRequest request, String userAgent) {
        validarCadastro(request);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String cpf = request.cpf().replaceAll("\\D", "");
        if (contaRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este email já está cadastrado.");
        }

        Conta conta = new Conta();
        conta.setNome(request.nome().trim());
        conta.setCpf(cpf);
        conta.setEmail(email);
        conta.setSenha(passwordHasher.hash(request.senha()));
        conta.setTelefone(request.telefone().trim());
        conta.setAtivo(true);
        contaRepository.save(conta);

        Consumidor consumidor = new Consumidor();
        consumidor.setConta(conta);
        consumidorRepository.save(consumidor);
        return response(conta, "CONSUMIDOR", userAgent);
    }

    @Transactional
    public ContaResponse cadastrarFeirante(CadastroFeiranteRequest request, String userAgent) {
        validarCadastroFeirante(request);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String cpf = request.cpf().replaceAll("\\D", "");
        String cnpj = request.cnpj().replaceAll("\\D", "");
        if (contaRepository.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este email já está cadastrado.");
        }
        if (feiranteRepository.existsByCnpj(cnpj)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Este CNPJ já está cadastrado.");
        }
        if (feiranteRepository.existsByMatricula(request.matricula().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta matrícula já está cadastrada.");
        }

        Conta conta = new Conta();
        conta.setNome(request.nome().trim());
        conta.setCpf(cpf);
        conta.setEmail(email);
        conta.setSenha(passwordHasher.hash(request.senha()));
        conta.setTelefone(request.telefone().trim());
        conta.setAtivo(true);
        contaRepository.save(conta);

        Feirante feirante = new Feirante();
        feirante.setConta(conta);
        feirante.setNomeFantasia(request.nomeFantasia().trim());
        feirante.setCnpj(cnpj);
        feirante.setCcm(request.ccm().trim());
        feirante.setMatricula(request.matricula().trim());
        feirante.setGrupoComercio(request.grupoComercio().trim());
        feiranteRepository.save(feirante);
        return response(conta, "FEIRANTE", userAgent);
    }

    @Transactional
    public ContaResponse login(LoginRequest request, String userAgent) {
        if (request == null || vazio(request.email()) || vazio(request.senha())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe email e senha.");
        }
        Conta conta = contaRepository.findByEmailIgnoreCase(request.email().trim())
                .filter(Conta::isAtivo)
                .filter(c -> passwordHasher.matches(request.senha(), c.getSenha()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email ou senha inválidos."));

        String tipo = feiranteRepository.existsByConta_Id(conta.getId())
                ? "FEIRANTE"
                : consumidorRepository.existsByConta_Id(conta.getId()) ? "CONSUMIDOR" : "CONTA";
        return response(conta, tipo, userAgent);
    }

    private void validarCadastroFeirante(CadastroFeiranteRequest request) {
        if (request == null || vazio(request.nome()) || vazio(request.cpf()) || vazio(request.email())
                || vazio(request.senha()) || vazio(request.telefone()) || vazio(request.nomeFantasia())
                || vazio(request.cnpj()) || vazio(request.ccm()) || vazio(request.matricula())
                || vazio(request.grupoComercio())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Preencha todos os campos obrigatórios.");
        }
        String cpf = request.cpf().replaceAll("\\D", "");
        if (cpf.length() != 11) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O CPF deve conter 11 dígitos.");
        }
        String cnpj = request.cnpj().replaceAll("\\D", "");
        if (cnpj.length() != 14) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O CNPJ deve conter 14 dígitos.");
        }
        if (request.nomeFantasia().trim().length() > 100 || request.ccm().trim().length() > 20
                || request.matricula().trim().length() > 20 || request.grupoComercio().trim().length() > 60) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Confira o tamanho dos dados comerciais informados.");
        }
        if (request.senha().length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A senha deve ter pelo menos 8 caracteres.");
        }
    }

    private void validarCadastro(CadastroConsumidorRequest request) {
        if (request == null || vazio(request.nome()) || vazio(request.cpf()) || vazio(request.email())
                || vazio(request.senha()) || vazio(request.telefone())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Preencha todos os campos obrigatórios.");
        }
        String cpf = request.cpf().replaceAll("\\D", "");
        if (cpf.length() != 11) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O CPF deve conter 11 dígitos.");
        }
        if (request.senha().length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A senha deve ter pelo menos 8 caracteres.");
        }
    }

    private boolean vazio(String value) {
        return value == null || value.isBlank();
    }

    // Cada login abre uma sessão (um aparelho em "Dispositivos conectados").
    // Feirante: o login já leva o plano escolhido (em outro aparelho o app não pede o plano de novo).
    private ContaResponse response(Conta conta, String tipo, String userAgent) {
        String plano = "FEIRANTE".equals(tipo)
                ? feiranteRepository.findByConta_Id(conta.getId()).map(PlanoService::codigoDo).orElse(null)
                : null;
        return new ContaResponse(conta.getId(), conta.getNome(), conta.getEmail(), tipo,
                sessaoService.abrir(conta.getId(), userAgent), plano);
    }
}
