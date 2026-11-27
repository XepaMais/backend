package br.com.xepamais.api.auth;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Protege o login contra tentativa e erro de senha (força bruta):
// depois de 5 senhas erradas para o mesmo e-mail, o login dele fica bloqueado por 15 minutos.
// A chave é o e-mail (e não o IP), porque o IP que chega pela Vercel pode ser forjado no cabeçalho.
@Component
public class LimiteTentativas {
    static final int TENTATIVAS_MAXIMAS = 5;
    static final long BLOQUEIO_MS = 15 * 60 * 1000L;

    private record Registro(int falhas, long primeiraFalha) {}

    private final Map<String, Registro> registros = new ConcurrentHashMap<>();
    private final Clock relogio;

    public LimiteTentativas() {
        this(Clock.systemUTC());
    }

    LimiteTentativas(Clock relogio) {
        this.relogio = relogio;
    }

    public void conferir(String email) {
        Registro registro = registros.get(chave(email));
        if (registro != null && registro.falhas() >= TENTATIVAS_MAXIMAS
                && relogio.millis() - registro.primeiraFalha() < BLOQUEIO_MS) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Muitas tentativas de login. Aguarde 15 minutos e tente de novo.");
        }
    }

    public void registrarFalha(String email) {
        long agora = relogio.millis();
        registros.compute(chave(email), (k, r) -> r == null || agora - r.primeiraFalha() >= BLOQUEIO_MS
                ? new Registro(1, agora) : new Registro(r.falhas() + 1, r.primeiraFalha()));
        if (registros.size() > 50_000) registros.clear(); // evita crescer sem limite
    }

    public void limpar(String email) {
        registros.remove(chave(email));
    }

    private static String chave(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
