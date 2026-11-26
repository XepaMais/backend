package br.com.xepamais.api.feira;

import br.com.xepamais.api.database.model.Feira;
import br.com.xepamais.api.database.repository.FeiraRepository;
import br.com.xepamais.api.feira.dto.FeiraDtos.FeiraResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;

@Service
public class FeiraService {
    private static final double RAIO_TERRA_KM = 6371;

    private final FeiraRepository feiraRepository;

    public FeiraService(FeiraRepository feiraRepository) {
        this.feiraRepository = feiraRepository;
    }

    // Todas as feiras cadastradas.
    @Transactional(readOnly = true)
    public List<FeiraResponse> listar() {
        return feiraRepository.findAll().stream()
                .map(this::response)
                .toList();
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
        return new FeiraResponse(feira.getId(), feira.getNome(), feira.getEndereco(), feira.getNumero(),
                feira.getCep(), feira.getCidade(), feira.getUf(), feira.getDias().stream().sorted().toList(),
                feira.getHorarioInicio(), feira.getHorarioFim(), feira.getLatitude(), feira.getLongitude());
    }
}
