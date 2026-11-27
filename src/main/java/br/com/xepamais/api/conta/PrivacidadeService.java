package br.com.xepamais.api.conta;

import br.com.xepamais.api.auth.PasswordHasher;
import br.com.xepamais.api.chat.ConversaService;
import br.com.xepamais.api.consumidor.ConsumidorService;
import br.com.xepamais.api.consumidor.PedidoService;
import br.com.xepamais.api.database.model.Conta;
import br.com.xepamais.api.database.model.Consumidor;
import br.com.xepamais.api.database.model.DispositivoSessao;
import br.com.xepamais.api.database.model.Feirante;
import br.com.xepamais.api.database.model.Xepa;
import br.com.xepamais.api.database.repository.ConsumidorRepository;
import br.com.xepamais.api.database.repository.ContaRepository;
import br.com.xepamais.api.database.repository.DispositivoSessaoRepository;
import br.com.xepamais.api.database.repository.FeiranteRepository;
import br.com.xepamais.api.database.repository.XepaRepository;
import br.com.xepamais.api.financeiro.FinanceiroService;
import br.com.xepamais.api.xepinha.XepinhaService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Direitos do titular dos dados (LGPD): ver os dados, consentimento e excluir a conta
@Service
public class PrivacidadeService {
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    public record ConsentimentoResponse(LocalDateTime aceiteTermosEm, String versaoTermos, boolean aceiteMarketing) {}

    public record ConsentimentoRequest(Boolean aceiteMarketing) {}

    public record ExclusaoRequest(String senha) {}

    private final ContaRepository contaRepository;
    private final ConsumidorRepository consumidorRepository;
    private final FeiranteRepository feiranteRepository;
    private final DispositivoSessaoRepository sessaoRepository;
    private final XepaRepository xepaRepository;
    private final PasswordHasher passwordHasher;
    private final ConsumidorService consumidorService;
    private final PedidoService pedidoService;
    private final ConversaService conversaService;
    private final FinanceiroService financeiroService;
    private final XepinhaService xepinhaService;
    private final SecureRandom random = new SecureRandom();

    public PrivacidadeService(ContaRepository contaRepository, ConsumidorRepository consumidorRepository,
                              FeiranteRepository feiranteRepository, DispositivoSessaoRepository sessaoRepository,
                              XepaRepository xepaRepository, PasswordHasher passwordHasher,
                              ConsumidorService consumidorService, PedidoService pedidoService,
                              ConversaService conversaService, FinanceiroService financeiroService,
                              XepinhaService xepinhaService) {
        this.contaRepository = contaRepository;
        this.consumidorRepository = consumidorRepository;
        this.feiranteRepository = feiranteRepository;
        this.sessaoRepository = sessaoRepository;
        this.xepaRepository = xepaRepository;
        this.passwordHasher = passwordHasher;
        this.consumidorService = consumidorService;
        this.pedidoService = pedidoService;
        this.conversaService = conversaService;
        this.financeiroService = financeiroService;
        this.xepinhaService = xepinhaService;
    }

    // Tudo o que o Xepa+ guarda sobre a pessoa, num JSON só
    @Transactional(readOnly = true)
    public Map<String, Object> exportar(Integer idConta) {
        Conta conta = conta(idConta);
        Feirante feirante = feiranteRepository.findByConta_Id(idConta).orElse(null);
        boolean consumidor = consumidorRepository.existsByConta_Id(idConta);

        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("geradoEm", LocalDateTime.now(FUSO));
        dados.put("aviso", "Cópia dos seus dados pessoais guardados pelo Xepa+ (LGPD, art. 18).");

        Map<String, Object> perfil = new LinkedHashMap<>();
        perfil.put("id", conta.getId());
        if (feirante != null) {
            perfil.put("tipo", "FEIRANTE");
        } else if (consumidor) {
            perfil.put("tipo", "CONSUMIDOR");
        } else {
            perfil.put("tipo", "CONTA");
        }
        perfil.put("nome", conta.getNome());
        perfil.put("email", conta.getEmail());
        perfil.put("cpf", conta.getCpf());
        perfil.put("telefone", conta.getTelefone());
        perfil.put("dataNascimento", conta.getDataNascimento());
        perfil.put("foto", conta.getFoto());
        perfil.put("dataCadastro", conta.getDataCadastro());
        dados.put("perfil", perfil);
        dados.put("consentimento", consentimento(conta));

        if (feirante != null) {
            Map<String, Object> negocio = new LinkedHashMap<>();
            negocio.put("nomeFantasia", feirante.getNomeFantasia());
            negocio.put("cnpj", feirante.getCnpj());
            negocio.put("ccm", feirante.getCcm());
            negocio.put("matricula", feirante.getMatricula());
            negocio.put("validadeMatricula", feirante.getValidadeMatricula());
            negocio.put("titular", feirante.getTitular());
            negocio.put("grupoComercio", feirante.getGrupoComercio());
            if (feirante.getPlano() == null) {
                negocio.put("plano", null);
            } else {
                negocio.put("plano", feirante.getPlano().getCodigo());
            }
            dados.put("feirante", negocio);

            // Se uma parte der erro, coloca uma lista vazia e continua
            try {
                dados.put("vendas", pedidoService.vendasDoFeirante(idConta));
            } catch (ResponseStatusException e) {
                dados.put("vendas", new ArrayList<>());
            }
            try {
                dados.put("conversas", conversaService.listar("feirante", idConta));
            } catch (ResponseStatusException e) {
                dados.put("conversas", new ArrayList<>());
            }
            try {
                dados.put("conversasXepinha", xepinhaService.conversas(idConta));
            } catch (ResponseStatusException e) {
                dados.put("conversasXepinha", new ArrayList<>());
            }
            try {
                dados.put("rendas", financeiroService.listarRendas(idConta, null, null));
            } catch (ResponseStatusException e) {
                dados.put("rendas", new ArrayList<>());
            }
            try {
                dados.put("despesas", financeiroService.listarDespesas(idConta, null, null));
            } catch (ResponseStatusException e) {
                dados.put("despesas", new ArrayList<>());
            }
            try {
                dados.put("saques", financeiroService.listarSaques(idConta));
            } catch (ResponseStatusException e) {
                dados.put("saques", new ArrayList<>());
            }
        }
        if (consumidor) {
            try {
                dados.put("recebimento", consumidorService.recebimento(idConta));
            } catch (ResponseStatusException e) {
                dados.put("recebimento", new ArrayList<>());
            }
            try {
                dados.put("pedidos", pedidoService.listar(idConta));
            } catch (ResponseStatusException e) {
                dados.put("pedidos", new ArrayList<>());
            }
            try {
                dados.put("conversas", conversaService.listar("consumidor", idConta));
            } catch (ResponseStatusException e) {
                dados.put("conversas", new ArrayList<>());
            }
        }

        List<Map<String, String>> dispositivos = new ArrayList<>();
        for (DispositivoSessao s : sessaoRepository.ativas(idConta, LocalDateTime.now(FUSO))) {
            Map<String, String> aparelho = new LinkedHashMap<>();
            aparelho.put("aparelho", String.valueOf(s.getNomeAparelho()));
            aparelho.put("navegador", String.valueOf(s.getNavegador()));
            aparelho.put("conectadoEm", String.valueOf(s.getDataCriacao()));
            dispositivos.add(aparelho);
        }
        dados.put("dispositivos", dispositivos);
        return dados;
    }

    @Transactional(readOnly = true)
    public ConsentimentoResponse consentimento(Integer idConta) {
        return consentimento(conta(idConta));
    }

    // A pessoa pode aceitar ou recusar o marketing quando quiser
    @Transactional
    public ConsentimentoResponse alterarConsentimento(Integer idConta, ConsentimentoRequest request) {
        if (request == null || request.aceiteMarketing() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe aceiteMarketing (true ou false).");
        }
        Conta conta = conta(idConta);
        conta.setAceiteMarketing(request.aceiteMarketing());
        return consentimento(conta);
    }

    // Exclusão da conta: apaga os dados pessoais e desconecta todos os aparelhos
    @Transactional
    public void excluir(Integer idConta, ExclusaoRequest request) {
        Conta conta = conta(idConta);
        if (request == null || request.senha() == null || !passwordHasher.matches(request.senha(), conta.getSenha())) {
            // 403 porque no front o 401 desloga a pessoa
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Senha incorreta.");
        }

        String sufixo = String.format("%010d", conta.getId());
        conta.setNome("Usuário removido");
        conta.setEmail("removido-" + conta.getId() + "@xepamais.invalid");
        // O CPF é obrigatório no banco, então coloca um valor que nunca é um CPF de verdade
        conta.setCpf("X" + sufixo);
        conta.setTelefone(null);
        conta.setDataNascimento(null);
        conta.setFoto(null);
        // Senha aleatória para ninguém mais conseguir entrar
        conta.setSenha(passwordHasher.hash(senhaAleatoria()));
        conta.setAceiteMarketing(false);
        conta.setAtivo(false);
        conta.setDataExclusao(LocalDateTime.now(FUSO));

        Consumidor consumidor = consumidorRepository.findByConta_Id(idConta).orElse(null);
        if (consumidor != null) {
            anonimizarConsumidor(consumidor);
        }
        Feirante feirante = feiranteRepository.findByConta_Id(idConta).orElse(null);
        if (feirante != null) {
            anonimizarFeirante(feirante);
        }
        // -1 não é id de nenhuma sessão, então encerra todas
        sessaoRepository.revogarOutras(idConta, -1);
    }

    private void anonimizarConsumidor(Consumidor consumidor) {
        consumidor.setCepEntrega(null);
        consumidor.setLogradouroEntrega(null);
        consumidor.setNumeroEntrega(null);
        consumidor.setComplementoEntrega(null);
        consumidor.setBairroEntrega(null);
        consumidor.setCidadeEntrega(null);
        consumidor.setUfEntrega(null);
    }

    private void anonimizarFeirante(Feirante feirante) {
        feirante.setNomeFantasia("Banca removida");
        feirante.setCnpj(null);
        feirante.setCcm(null);
        feirante.setMatricula(null);
        feirante.setValidadeMatricula(null);
        feirante.setTitular(null);
        // As xepas do feirante saem da vitrine
        for (Xepa x : xepaRepository.doFeirante(feirante.getConta().getId())) {
            x.setStatus("CANCELADA");
        }
    }

    private ConsentimentoResponse consentimento(Conta conta) {
        return new ConsentimentoResponse(conta.getAceiteTermosEm(), conta.getVersaoTermos(), conta.isAceiteMarketing());
    }

    private Conta conta(Integer idConta) {
        Conta conta = contaRepository.findById(idConta).orElse(null);
        if (conta == null || !conta.isAtivo()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conta não encontrada.");
        }
        return conta;
    }

    private String senhaAleatoria() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
