package br.com.xepamais.api.xepinha;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

// Chama a API do Gemini (Google) pelo servidor: a chave fica só em GEMINI_API_KEY na EC2,
// nunca no navegador. Tenta de novo em 503/429 e, se continuar, usa o modelo reserva.
@Component
public class GeminiCliente implements ClienteIa {
    private static final Logger log = LoggerFactory.getLogger(GeminiCliente.class);
    private static final String URL = "https://generativelanguage.googleapis.com/v1beta/models/{modelo}:generateContent";
    private static final int TENTATIVAS_POR_MODELO = 2;

    private final String chave;
    private final List<String> modelos;
    private final RestClient http;

    public GeminiCliente(@Value("${app.ia.gemini-api-key:}") String chave,
                         @Value("${app.ia.modelos:gemini-3.5-flash-lite,gemini-3.1-flash-lite}") String modelos) {
        this.chave = chave == null ? "" : chave.trim();
        this.modelos = Arrays.stream(modelos.split(",")).map(String::trim).filter(m -> !m.isEmpty()).toList();
        SimpleClientHttpRequestFactory tempo = new SimpleClientHttpRequestFactory();
        tempo.setConnectTimeout(10_000);
        tempo.setReadTimeout(60_000);
        this.http = RestClient.builder().requestFactory(tempo).build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public String responder(String instrucoes, List<Turno> historico, String pergunta) {
        if (chave.isEmpty()) {
            throw new IaIndisponivelException("GEMINI_API_KEY não configurada.", false, null);
        }

        List<Map<String, Object>> conteudo = new ArrayList<>();
        for (Turno turno : historico) {
            conteudo.add(Map.of("role", turno.papel(), "parts", List.of(Map.of("text", turno.texto()))));
        }
        conteudo.add(Map.of("role", "user", "parts", List.of(Map.of("text", pergunta))));
        Map<String, Object> corpo = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", instrucoes))),
                "contents", conteudo);

        RuntimeException ultimoErro = null;
        boolean sobrecarga = false;
        for (String modelo : modelos) {
            for (int tentativa = 1; tentativa <= TENTATIVAS_POR_MODELO; tentativa++) {
                try {
                    Map<String, Object> resposta = http.post()
                            .uri(URL, modelo)
                            .header("x-goog-api-key", chave)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(corpo)
                            .retrieve()
                            .body(Map.class);
                    String texto = textoDa(resposta);
                    if (texto != null && !texto.isBlank()) return texto.trim();
                    ultimoErro = new IllegalStateException("Resposta vazia do Gemini.");
                } catch (RestClientResponseException ex) {
                    ultimoErro = ex;
                    int status = ex.getStatusCode().value();
                    boolean temporario = status == 503 || status == 429;
                    sobrecarga = temporario;
                    log.warn("Gemini {} respondeu {} (tentativa {}/{})", modelo, status, tentativa, TENTATIVAS_POR_MODELO);
                    if (!temporario) break; // erro de verdade (chave, modelo): tenta o próximo modelo
                    esperar(1000L * tentativa);
                } catch (RuntimeException ex) {
                    ultimoErro = ex;
                    log.warn("Falha ao chamar o Gemini {}: {}", modelo, ex.getMessage());
                }
            }
        }
        throw new IaIndisponivelException("A Xepinha não respondeu.", sobrecarga, ultimoErro);
    }

    @SuppressWarnings("unchecked")
    private static String textoDa(Map<String, Object> resposta) {
        if (resposta == null) return null;
        List<Map<String, Object>> candidatos = (List<Map<String, Object>>) resposta.get("candidates");
        if (candidatos == null || candidatos.isEmpty()) return null;
        Map<String, Object> conteudo = (Map<String, Object>) candidatos.get(0).get("content");
        if (conteudo == null) return null;
        List<Map<String, Object>> partes = (List<Map<String, Object>>) conteudo.get("parts");
        if (partes == null) return null;
        StringBuilder texto = new StringBuilder();
        for (Map<String, Object> parte : partes) {
            Object t = parte.get("text");
            if (t != null) texto.append(t);
        }
        return texto.toString();
    }

    private static void esperar(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
