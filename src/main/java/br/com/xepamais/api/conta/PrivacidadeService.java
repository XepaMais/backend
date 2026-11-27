package br.com.xepamais.api.conta;

import br.com.xepamais.api.auth.PasswordHasher;
import br.com.xepamais.api.chat.ConversaService;
import br.com.xepamais.api.consumidor.ConsumidorService;
import br.com.xepamais.api.consumidor.PedidoService;
import br.com.xepamais.api.database.model.Conta;
import br.com.xepamais.api.database.model.Consumidor;
import br.com.xepamais.api.database.model.Feirante;
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
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

// Direitos do titular dos dados (LGPD, art. 18): acesso/portabilidade, consentimento e eliminação.
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

    // Tudo o que o Xepa+ guarda sobre a pessoa, num JSON só (o front baixa como arquivo).
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
        perfil.put("tipo", feirante != null ? "FEIRANTE" : consumidor ? "CONSUMIDOR" : "CONTA");
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
            negocio.put("plano", feirante.getPlano() == null ? null : feirante.getPlano().getCodigo());
            dados.put("feirante", negocio);
            dados.put("vendas", seguro(() -> pedidoService.vendasDoFeirante(idConta)));
            dados.put("conversas", seguro(() -> conversaService.listar("feirante", idConta)));
            dados.put("conversasXepinha", seguro(() -> xepinhaService.conversas(idConta)));
            dados.put("rendas", seguro(() -> financeiroService.listarRendas(idConta, null, null)));
            dados.put("despesas", seguro(() -> financeiroService.listarDespesas(idConta, null, null)));
            dados.put("saques", seguro(() -> financeiroService.listarSaques(idConta)));
        }
        if (consumidor) {
            dados.put("recebimento", seguro(() -> consumidorService.recebimento(idConta)));
            dados.put("pedidos", seguro(() -> pedidoService.listar(idConta)));
            dados.put("conversas", seguro(() -> conversaService.listar("consumidor", idConta)));
        }
        dados.put("dispositivos", sessaoRepository.ativas(idConta, LocalDateTime.now(FUSO)).stream()
                .map(s -> Map.of("aparelho", String.valueOf(s.getNomeAparelho()),
                        "navegador", String.valueOf(s.getNavegador()),
                        "conectadoEm", String.valueOf(s.getDataCriacao())))
                .toList());
        return dados;
    }

    @Transactional(readOnly = true)
    public ConsentimentoResponse consentimento(Integer idConta) {
        return consentimento(conta(idConta));
    }

    // A pessoa pode dar ou retirar o consentimento de marketing quando quiser (art. 8º, § 5º).
    @Transactional
    public ConsentimentoResponse alterarConsentimento(Integer idConta, ConsentimentoRequest request) {
        if (request == null || request.aceiteMarketing() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe aceiteMarketing (true ou false).");
        }
        Conta conta = conta(idConta);
        conta.setAceiteMarketing(request.aceiteMarketing());
        return consentimento(conta);
    }

    // Exclusão da conta: os dados pessoais são anonimizados e todos os logins são encerrados.
    // Pedidos, pagamentos e lançamentos financeiros continuam guardados (sem identificar a pessoa)
    // porque a lei fiscal obriga a manter esses registros (LGPD, art. 16, I).
    @Transactional
    public void excluir(Integer idConta, ExclusaoRequest request) {
        Conta conta = conta(idConta);
        if (request == null || request.senha() == null || !passwordHasher.matches(request.senha(), conta.getSenha())) {
            // 403 (e não 401): no front, 401 significa "sessão expirada" e desloga a pessoa.
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Senha incorreta.");
        }

        String sufixo = String.format("%010d", conta.getId());
        conta.setNome("Usuário removido");
        conta.setEmail("removido-" + conta.getId() + "@xepamais.invalid");
        conta.setCpf("X" + sufixo); // CPF é obrigatório e único no banco: marcador que nunca é um CPF real
        conta.setTelefone(null);
        conta.setDataNascimento(null);
        conta.setFoto(null);
        conta.setSenha(passwordHasher.hash(senhaAleatoria())); // ninguém mais consegue entrar
        conta.setAceiteMarketing(false);
        conta.setAtivo(false);
        conta.setDataExclusao(LocalDateTime.now(FUSO));

        consumidorRepository.findByConta_Id(idConta).ifPresent(this::anonimizarConsumidor);
        feiranteRepository.findByConta_Id(idConta).ifPresent(this::anonimizarFeirante);
        sessaoRepository.revogarOutras(idConta, -1); // -1 não é id de sessão: encerra todas
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
        // As ofertas do feirante saem do catálogo.
        xepaRepository.doFeirante(feirante.getConta().getId()).forEach(x -> x.setStatus("CANCELADA"));
    }

    private ConsentimentoResponse consentimento(Conta conta) {
        return new ConsentimentoResponse(conta.getAceiteTermosEm(), conta.getVersaoTermos(), conta.isAceiteMarketing());
    }

    private Conta conta(Integer idConta) {
        return contaRepository.findById(idConta).filter(Conta::isAtivo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conta não encontrada."));
    }

    private String senhaAleatoria() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    // Uma parte que falhar (ex.: feirante sem plano com Xepinha) não impede o resto da exportação.
    private static Object seguro(Supplier<?> parte) {
        try {
            return parte.get();
        } catch (ResponseStatusException ex) {
            return List.of();
        }
    }
}
