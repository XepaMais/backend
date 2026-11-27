package br.com.xepamais.api.feira;

import br.com.xepamais.api.database.model.Feira;
import br.com.xepamais.api.database.model.Feirante;
import br.com.xepamais.api.database.model.FuncionarioFeira;
import br.com.xepamais.api.database.repository.FeiraRepository;
import br.com.xepamais.api.database.repository.FeiranteRepository;
import br.com.xepamais.api.database.repository.FuncionarioFeiraRepository;
import br.com.xepamais.api.feira.dto.FeiraDtos.FeiraRequest;
import br.com.xepamais.api.feira.dto.FeiraDtos.FeiraResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    // Todas as feiras, em ordem de nome
    @Transactional(readOnly = true)
    public List<FeiraResponse> listar() {
        List<Feira> feiras = new ArrayList<>(feiraRepository.findAll());
        ordenarPorNome(feiras);
        return respostas(feiras);
    }

    // Busca pelo nome: "lapa" acha "Feira Livre da Lapa"
    @Transactional(readOnly = true)
    public List<FeiraResponse> buscarPorNome(String busca) {
        String termo = semAcento(busca);
        // Primeiro as que começam com o termo, depois as que só contêm
        List<Feira> comecam = new ArrayList<>();
        List<Feira> contem = new ArrayList<>();
        for (Feira feira : feiraRepository.findAll()) {
            String nome = semAcento(feira.getNome());
            if (nome.startsWith(termo)) {
                comecam.add(feira);
            } else if (nome.contains(termo)) {
                contem.add(feira);
            }
        }
        ordenarPorNome(comecam);
        ordenarPorNome(contem);
        List<Feira> todas = new ArrayList<>();
        todas.addAll(comecam);
        todas.addAll(contem);
        return respostas(todas);
    }

    private static String semAcento(String texto) {
        if (texto == null) {
            return "";
        }
        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.toLowerCase(Locale.ROOT).trim();
    }

    // Feiras a até "raioKm" do ponto, da mais perto para a mais longe
    @Transactional(readOnly = true)
    public List<FeiraResponse> listarProximas(double lat, double lng, double raioKm) {
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Latitude ou longitude inválida.");
        }
        if (raioKm <= 0 || raioKm > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O raio deve ser entre 0 e 100 km.");
        }

        List<Feira> perto = new ArrayList<>();
        for (Feira feira : feiraRepository.findAll()) {
            if (feira.getLatitude() != null && feira.getLongitude() != null
                    && distanciaKm(lat, lng, feira) <= raioKm) {
                perto.add(feira);
            }
        }

        // Ordena pela distância
        for (int i = 1; i < perto.size(); i++) {
            int j = i;
            while (j > 0 && distanciaKm(lat, lng, perto.get(j - 1)) > distanciaKm(lat, lng, perto.get(j))) {
                Feira temp = perto.get(j);
                perto.set(j, perto.get(j - 1));
                perto.set(j - 1, temp);
                j--;
            }
        }
        return respostas(perto);
    }

    // Feiras do feirante

    @Transactional(readOnly = true)
    public List<FeiraResponse> listarDoFeirante(Integer idConta) {
        Feirante feirante = feirante(idConta);
        Map<Integer, List<String>> funcionarios = funcionariosPorFeira(feirante);
        List<FeiraResponse> lista = new ArrayList<>();
        for (Feira feira : feiraRepository.doFeirante(idConta)) {
            List<String> nomes = funcionarios.get(feira.getId());
            if (nomes == null) {
                nomes = new ArrayList<>();
            }
            lista.add(response(feira, nomes));
        }
        return lista;
    }

    // Cria a feira e liga ao feirante
    @Transactional
    public FeiraResponse criar(Integer idConta, FeiraRequest request) {
        Feirante feirante = feirante(idConta);
        Feira feira = new Feira();
        preencher(feira, request);
        feira = feiraRepository.save(feira);

        if (feirante.getFeiras() == null) {
            feirante.setFeiras(new HashSet<>());
        }
        feirante.getFeiras().add(feira);
        feiranteRepository.saveAndFlush(feirante);
        return response(feira, salvarFuncionarios(feirante, feira, request.funcionarios()));
    }

    @Transactional
    public FeiraResponse atualizar(Integer idConta, Integer idFeira, FeiraRequest request) {
        Feirante feirante = feirante(idConta);
        Feira feira = feiraDoFeirante(idConta, idFeira);
        preencher(feira, request);
        List<String> funcionarios;
        if (request.funcionarios() == null) {
            funcionarios = funcionariosPorFeira(feirante).get(feira.getId());
            if (funcionarios == null) {
                funcionarios = new ArrayList<>();
            }
        } else {
            funcionarios = salvarFuncionarios(feirante, feira, request.funcionarios());
        }
        return response(feira, funcionarios);
    }

    // Troca a lista de funcionários do feirante nessa feira
    private List<String> salvarFuncionarios(Feirante feirante, Feira feira, List<String> nomes) {
        funcionarioRepository.apagar(feirante.getId(), feira.getId());
        List<String> limpos = new ArrayList<>();
        if (nomes == null) {
            return limpos;
        }
        for (String nome : nomes) {
            if (nome == null) {
                continue;
            }
            nome = nome.trim();
            if (!nome.isEmpty() && !limpos.contains(nome) && limpos.size() < 20) {
                limpos.add(nome);
            }
        }
        for (String nome : limpos) {
            if (nome.length() > 80) {
                throw invalido("O nome do funcionário pode ter até 80 caracteres.");
            }
            FuncionarioFeira funcionario = new FuncionarioFeira();
            funcionario.setFeirante(feirante);
            funcionario.setFeira(feira);
            funcionario.setNome(nome);
            funcionarioRepository.save(funcionario);
        }
        return limpos;
    }

    // Funcionários do feirante, separados por feira
    public Map<Integer, List<String>> funcionariosPorFeira(Feirante feirante) {
        Map<Integer, List<String>> mapa = new HashMap<>();
        for (FuncionarioFeira f : funcionarioRepository.findByFeirante_IdOrderByIdAsc(feirante.getId())) {
            Integer idFeira = f.getFeira().getId();
            if (mapa.get(idFeira) == null) {
                mapa.put(idFeira, new ArrayList<>());
            }
            mapa.get(idFeira).add(f.getNome());
        }
        return mapa;
    }

    // Se a feira tiver xepas ou entregas, o banco recusa e o controller responde 409
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
        Feirante feirante = feiranteRepository.findByConta_Id(idConta).orElse(null);
        if (feirante == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Feirante não encontrado.");
        }
        return feirante;
    }

    private Feira feiraDoFeirante(Integer idConta, Integer idFeira) {
        feirante(idConta);
        Feira feira = feiraRepository.doFeirante(idConta, idFeira).orElse(null);
        if (feira == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Feira não encontrada.");
        }
        return feira;
    }

    private void preencher(Feira feira, FeiraRequest request) {
        if (request == null) {
            throw invalido("Dados da feira não enviados.");
        }

        String nome = texto(request.nome(), "Informe o nome da feira.", 100);
        String endereco = texto(request.endereco(), "Informe o endereço da feira.", 150);
        String cidade = texto(request.cidade(), "Informe a cidade da feira.", 80);
        String uf = texto(request.uf(), "Informe a UF da feira.", 2).toUpperCase(Locale.ROOT);
        if (!uf.matches("[A-Z]{2}")) {
            throw invalido("UF inválida.");
        }

        String cep = "";
        if (request.cep() != null) {
            cep = request.cep().replaceAll("\\D", "");
        }
        if (!cep.isEmpty() && cep.length() != 8) {
            throw invalido("O CEP precisa ter 8 números.");
        }

        String numero = "";
        if (request.numero() != null) {
            numero = request.numero().trim();
        }
        if (numero.length() > 10) {
            throw invalido("O número pode ter até 10 caracteres.");
        }

        List<Integer> dias = new ArrayList<>();
        if (request.dias() != null) {
            dias = request.dias();
        }
        for (Integer dia : dias) {
            if (dia == null || dia < 1 || dia > 7) {
                throw invalido("Dia da semana inválido (use 1=segunda ... 7=domingo).");
            }
        }

        if (request.horarioInicio() != null && request.horarioFim() != null
                && !request.horarioFim().isAfter(request.horarioInicio())) {
            throw invalido("O horário de fim precisa ser depois do início.");
        }

        BigDecimal lat = request.latitude();
        BigDecimal lng = request.longitude();
        if (lat == null || lng == null) {
            throw invalido("Não foi possível localizar a feira no mapa.");
        }
        if (lat.abs().compareTo(BigDecimal.valueOf(90)) > 0 || lng.abs().compareTo(BigDecimal.valueOf(180)) > 0) {
            throw invalido("Latitude ou longitude inválida.");
        }

        feira.setNome(nome);
        feira.setEndereco(endereco);
        if (numero.isEmpty()) {
            feira.setNumero(null);
        } else {
            feira.setNumero(numero);
        }
        if (cep.isEmpty()) {
            feira.setCep(null);
        } else {
            feira.setCep(cep);
        }
        feira.setCidade(cidade);
        feira.setUf(uf);
        feira.getDias().clear();
        feira.getDias().addAll(dias);
        feira.setHorarioInicio(request.horarioInicio());
        feira.setHorarioFim(request.horarioFim());
        feira.setLatitude(lat.setScale(6, RoundingMode.HALF_UP));
        feira.setLongitude(lng.setScale(6, RoundingMode.HALF_UP));
    }

    private static String texto(String valor, String mensagem, int maximo) {
        String limpo = "";
        if (valor != null) {
            limpo = valor.trim();
        }
        if (limpo.isEmpty()) {
            throw invalido(mensagem);
        }
        if (limpo.length() > maximo) {
            throw invalido(mensagem.replace("Informe", "Encurte"));
        }
        return limpo;
    }

    private static ResponseStatusException invalido(String mensagem) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem);
    }

    // Ordena as feiras pelo nome (sem acento)
    private static void ordenarPorNome(List<Feira> feiras) {
        for (int i = 1; i < feiras.size(); i++) {
            int j = i;
            while (j > 0 && semAcento(feiras.get(j - 1).getNome()).compareTo(semAcento(feiras.get(j).getNome())) > 0) {
                Feira temp = feiras.get(j);
                feiras.set(j, feiras.get(j - 1));
                feiras.set(j - 1, temp);
                j--;
            }
        }
    }

    // Distância em linha reta, em km
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

    private List<FeiraResponse> respostas(List<Feira> feiras) {
        List<FeiraResponse> lista = new ArrayList<>();
        for (Feira feira : feiras) {
            lista.add(response(feira, new ArrayList<>()));
        }
        return lista;
    }

    private FeiraResponse response(Feira feira, List<String> funcionarios) {
        List<Integer> dias = new ArrayList<>(feira.getDias());
        Collections.sort(dias);
        return new FeiraResponse(feira.getId(), feira.getNome(), feira.getEndereco(), feira.getNumero(),
                feira.getCep(), feira.getCidade(), feira.getUf(), dias,
                feira.getHorarioInicio(), feira.getHorarioFim(), feira.getLatitude(), feira.getLongitude(),
                funcionarios);
    }
}
