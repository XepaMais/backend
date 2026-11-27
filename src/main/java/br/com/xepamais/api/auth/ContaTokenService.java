package br.com.xepamais.api.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

// Token das contas: "conta|idConta|idSessao|expiraEm" assinado com HMAC-SHA256
@Component
public class ContaTokenService {
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64_DEC = Base64.getUrlDecoder();

    public record DadosToken(Integer idConta, Integer idSessao) {}

    private final byte[] chave;

    public ContaTokenService(@Value("${app.auth.token-secret:}") String segredo) {
        if (segredo == null || segredo.isBlank()) {
            chave = new byte[32];
            new SecureRandom().nextBytes(chave);
        } else {
            chave = segredo.getBytes(StandardCharsets.UTF_8);
        }
    }

    public String gerar(Integer idConta, Integer idSessao, Instant expiraEm) {
        String texto = "conta|" + idConta + "|" + idSessao + "|" + expiraEm.getEpochSecond();
        String conteudo = B64.encodeToString(texto.getBytes(StandardCharsets.UTF_8));
        return conteudo + "." + B64.encodeToString(assinar(conteudo));
    }

    // Conta e sessão do token, se a assinatura confere e ele não venceu
    public Optional<DadosToken> ler(String token) {
        if (token == null) return Optional.empty();
        int ponto = token.indexOf('.');
        if (ponto < 1) return Optional.empty();
        String conteudo = token.substring(0, ponto);
        try {
            byte[] assinatura = B64_DEC.decode(token.substring(ponto + 1));
            if (!MessageDigest.isEqual(assinatura, assinar(conteudo))) return Optional.empty();
            String[] partes = new String(B64_DEC.decode(conteudo), StandardCharsets.UTF_8).split("\\|");
            if (partes.length != 4 || !"conta".equals(partes[0])) return Optional.empty();
            if (Instant.now().getEpochSecond() >= Long.parseLong(partes[3])) return Optional.empty();
            return Optional.of(new DadosToken(Integer.valueOf(partes[1]), Integer.valueOf(partes[2])));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
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
