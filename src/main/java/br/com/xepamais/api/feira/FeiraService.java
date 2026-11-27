package br.com.xepamais.api.feira;

import br.com.xepamais.api.database.model.Feira;
import br.com.xepamais.api.database.model.Feirante;
import br.com.xepamais.api.database.repository.FeiraRepository;
import br.com.xepamais.api.database.repository.FeiranteRepository;
import br.com.xepamais.api.database.model.FuncionarioFeira;
import br.com.xepamais.api.database.repository.FuncionarioFeiraRepository;
import br.com.xepamais.api.feira.dto.FeiraDtos.FeiraRequest;
import br.com.xepamais.api.feira.dto.FeiraDtos.FeiraResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class FeiraService {
    private static final double RAIO_TERRA_KM = 6371;

    private final FeiraRepository feiraRepository;
    private final FeiranteRepository feiranteRepository;
    private final FuncionarioFeiraRepository funcionarioRepository;

    public FeiraService(FeiraRepository feiraRepository, FeiranteRepository feiranteRepository,
                        FuncionarioFeiraRepository funcionarioRepository) {
        this.funcionarioRepository = funcionarioRepository;
        this.feiraRepository = feiraRepository;
        this.feiranteRepository = feiranteRepository;
    }

    // Todas as feiras cadastradas, em ordem de nome (mapa geral do consumidor).
    @Transactional(readOnly = true)
    public List<FeiraResponse> listar() {
        return feiraRepository.findAll().stream()
                .sorted(Comparator.comparing(feira -> semAcento(feira.getNome())))
                .map(this::response)
                .toList();
    }

    // Busca pelo nome: "lapa" acha "Feira Livre da Lapa". Quem começa com o termo vem primeiro.
    @Transactional(readOnly = true)
    public List<FeiraResponse> buscarPorNome(String busca) {
        String termo = semAcento(busca);
        return feiraRepository.findAll().stream()
                .filter(feira -> semAcento(feira.getNome()).contains(termo))
                .sorted(Comparator.comparing((Feira feira) -> !semAcento(feira.getNome()).startsWith(termo))
                        .thenComparing(feira -> semAcento(feira.getNome())))
                .map(this::response)
                .toList();
    }

    private static String semAcento(String texto) {
        if (texto == null) return "";
        return java.text.Normalizer.normalize(texto, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim();
    }

    // Feiras a até "raioKm" do ponto (lat, lng), da mais perto para a mais longe.
    @Transactional(readOnly = true)
    public List<FeiraResponse> listarProximas(double lat, double lng, double raioKm) {
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Latitude ou longitude inválida.");
        }
        if (raioKm <= 0 || raioKm > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O raio deve ser entre 0 e 100 km.");
        }

        return feiraRepository.findAll().stream()
                .filter(this::temCoordenada)
                .filter(feira -> distanciaKm(lat, lng, feira) <= raioKm)
                .sorted(Comparator.comparingDouble(feira -> distanciaKm(lat, lng, feira)))
                .map(this::response)
                .toList();
    }

    // ---------- Feiras do feirante ----------

    @Transactional(readOnly = true)
    public List<FeiraResponse> listarDoFeirante(Integer idConta) {
        Feirante feirante = feirante(idConta);
        Map<Integer, List<String>> funcionarios = funcionariosPorFeira(feirante);
        return feiraRepository.doFeirante(idConta).stream()
                .map(feira -> response(feira, funcionarios.getOrDefault(feira.getId(), List.of())))
                .toList();
    }

    // Cria a feira e liga ao feirante (tb_feirante_feira). Ela passa a aparecer no mapa do consumidor.
    @Transactional
    public FeiraResponse criar(Integer idConta, FeiraRequest request) {
        Feirante feirante = feirante(idConta);
        Feira feira = new Feira();
        preencher(feira, request);
        feira = feiraRepository.save(feira);

        if (feirante.getFeiras() == null) feirante.setFeiras(new HashSet<>());
        feirante.getFeiras().add(feira);
        return response(feira, salvarFuncionarios(feirante, feira, request.funcionarios()));
    }

    @Transactional
    public FeiraResponse atualizar(Integer idConta, Integer idFeira, FeiraRequest request) {
        Feirante feirante = feirante(idConta);
        Feira feira = feiraDoFeirante(idConta, idFeira);
        preencher(feira, request);
        List<String> funcionarios = request.funcionarios() == null
                ? funcionariosPorFeira(feirante).getOrDefault(feira.getId(), List.of())
                : salvarFuncionarios(feirante, feira, request.funcionarios());
        return response(feira, funcionarios);
    }

    // Troca a lista de funcionários do feirante nessa feira (nomes vazios ou repetidos são ignorados).
    private List<String> salvarFuncionarios(Feirante feirante, Feira feira, List<String> nomes) {
        funcionarioRepository.apagar(feirante.getId(), feira.getId());
        if (nomes == null) return List.of();
        List<String> limpos = nomes.stream()
                .filter(Objects::nonNull).map(String::trim).filter(nome -> !nome.isEmpty())
                .distinct().limit(20).toList();
        for (String nome : limpos) {
            if (nome.length() > 80) throw invalido("O nome do funcionário pode ter até 80 caracteres.");
            FuncionarioFeira funcionario = new FuncionarioFeira();
            funcionario.setFeirante(feirante);
            funcionario.setFeira(feira);
            funcionario.setNome(nome);
            funcionarioRepository.save(funcionario);
        }
        return limpos;
    }

    // Funcionários do feirante, agrupados por feira (usado também no perfil público do feirante).
    public Map<Integer, List<String>> funcionariosPorFeira(Feirante feirante) {
        return funcionarioRepository.findByFeirante_IdOrderByIdAsc(feirante.getId()).stream()
                .collect(Collectors.groupingBy(f -> f.getFeira().getId(),
                        Collectors.mapping(FuncionarioFeira::getNome, Collectors.toList())));
    }

    // Se a feira tiver xepas ou entregas, o banco recusa e o controller responde 409.
    @Transactional
    public void excluir(Integer idConta, Integer idFeira) {
        Feirante feirante = feirante(idConta);
        Feira feira = feiraDoFeirante(idConta, idFeira);
        funcionarioRepository.apagar(feirante.getId(), feira.getId());
        feirante.getFeiras().remove(feira);
        feiraRepository.delete(feira);
        feiraRepository.flush();
    }

    private Feirante feirante(Integer idConta) {
        return feiranteRepository.findByConta_Id(idConta)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Feirante não encontrado."));
    }

    private Feira feiraDoFeirante(Integer idConta, Integer idFeira) {
        feirante(idConta);
        return feiraRepository.doFeirante(idConta, idFeira)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Feira não encontrada."));
    }

    private void preencher(Feira feira, FeiraRequest request) {
        if (request == null) throw invalido("Dados da feira não enviados.");

        String nome = texto(request.nome(), "Informe o nome da feira.", 100);
        String endereco = texto(request.endereco(), "Informe o endereço da feira.", 150);
        String cidade = texto(request.cidade(), "Informe a cidade da feira.", 80);
        String uf = texto(request.uf(), "Informe a UF da feira.", 2).toUpperCase(Locale.ROOT);
        if (!uf.matches("[A-Z]{2}")) throw invalido("UF inválida.");

        String cep = request.cep() == null ? "" : request.cep().replaceAll("\\D", "");
        if (!cep.isEmpty() && cep.length() != 8) throw invalido("O CEP precisa ter 8 números.");

        String numero = request.numero() == null ? "" : request.numero().trim();
        if (numero.length() > 10) throw invalido("O número pode ter até 10 caracteres.");

        List<Integer> dias = request.dias() == null ? List.of() : request.dias();
        if (dias.stream().anyMatch(dia -> dia == null || dia < 1 || dia > 7)) {
            throw invalido("Dia da semana inválido (use 1=segunda ... 7=domingo).");
        }

        if (request.horarioInicio() != null && request.horarioFim() != null
                && !request.horarioFim().isAfter(request.horarioInicio())) {
            throw invalido("O horário de fim precisa ser depois do início.");
        }

        BigDecimal lat = request.latitude();
        BigDecimal lng = request.longitude();
        if (lat == null || lng == null) throw invalido("Não foi possível localizar a feira no mapa.");
        if (lat.abs().compareTo(BigDecimal.valueOf(90)) > 0 || lng.abs().compareTo(BigDecimal.valueOf(180)) > 0) {
            throw invalido("Latitude ou longitude inválida.");
        }

        feira.setNome(nome);
        feira.setEndereco(endereco);
        feira.setNumero(numero.isEmpty() ? null : numero);
        feira.setCep(cep.isEmpty() ? null : cep);
        feira.setCidade(cidade);
        feira.setUf(uf);
        feira.getDias().clear();
        feira.getDias().addAll(dias);
        feira.setHorarioInicio(request.horarioInicio());
        feira.setHorarioFim(request.horarioFim());
        feira.setLatitude(lat.setScale(6, java.math.RoundingMode.HALF_UP));
        feira.setLongitude(lng.setScale(6, java.math.RoundingMode.HALF_UP));
    }

    private static String texto(String valor, String mensagem, int maximo) {
        String limpo = valor == null ? "" : valor.trim();
        if (limpo.isEmpty()) throw invalido(mensagem);
        if (limpo.length() > maximo) throw invalido(mensagem.replace("Informe", "Encurte"));
        return limpo;
    }

    private static ResponseStatusException invalido(String mensagem) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
    }

    private boolean temCoordenada(Feira feira) {
        return feira.getLatitude() != null && feira.getLongitude() != null;
    }

    // Distância em linha reta (fórmula de Haversine), em km.
    private double distanciaKm(double lat, double lng, Feira feira) {
        double latFeira = feira.getLatitude().doubleValue();
        double lngFeira = feira.getLongitude().doubleValue();

        double dLat = Math.toRadians(latFeira - lat);
        double dLng = Math.toRadians(lngFeira - lng);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat)) * Math.cos(Math.toRadians(latFeira))
                * Math.pow(Math.sin(dLng / 2), 2);

        return 2 * RAIO_TERRA_KM * Math.asin(Math.sqrt(a));
    }

    private FeiraResponse response(Feira feira) {
        return response(feira, List.of());
    }

    private FeiraResponse response(Feira feira, List<String> funcionarios) {
        return new FeiraResponse(feira.getId(), feira.getNome(), feira.getEndereco(), feira.getNumero(),
                feira.getCep(), feira.getCidade(), feira.getUf(), feira.getDias().stream().sorted().toList(),
                feira.getHorarioInicio(), feira.getHorarioFim(), feira.getLatitude(), feira.getLongitude(),
                funcionarios);
    }
}
