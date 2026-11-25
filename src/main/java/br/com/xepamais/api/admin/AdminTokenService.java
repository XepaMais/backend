package br.com.xepamais.api.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

// Gera e confere o token do painel admin: "email|expiraEm" assinado com HMAC-SHA256.
// Sem ADMIN_TOKEN_SECRET a chave é sorteada ao iniciar, e reiniciar a API desloga o admin.
@Component
public class AdminTokenService {
    private static final Duration VALIDADE = Duration.ofHours(8);
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64_DEC = Base64.getUrlDecoder();

    private final byte[] chave;

    public AdminTokenService(@Value("${app.admin.token-secret:}") String segredo) {
        if (segredo == null || segredo.isBlank()) {
            chave = new byte[32];
            new SecureRandom().nextBytes(chave);
        } else {
            chave = segredo.getBytes(StandardCharsets.UTF_8);
        }
    }

    public String gerar(String email) {
        long expira = Instant.now().plus(VALIDADE).getEpochSecond();
        String conteudo = B64.encodeToString((email + "|" + expira).getBytes(StandardCharsets.UTF_8));
        return conteudo + "." + B64.encodeToString(assinar(conteudo));
    }

    public boolean valido(String token) {
        if (token == null) return false;
        int ponto = token.indexOf('.');
        if (ponto < 1) return false;
        String conteudo = token.substring(0, ponto);
        try {
            byte[] assinatura = B64_DEC.decode(token.substring(ponto + 1));
            if (!MessageDigest.isEqual(assinatura, assinar(conteudo))) return false;
            String texto = new String(B64_DEC.decode(conteudo), StandardCharsets.UTF_8);
            long expira = Long.parseLong(texto.substring(texto.lastIndexOf('|') + 1));
            return Instant.now().getEpochSecond() < expira;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private byte[] assinar(String conteudo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(chave, "HmacSHA256"));
            return mac.doFinal(conteudo.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Não foi possível assinar o token.", ex);
        }
    }
}
