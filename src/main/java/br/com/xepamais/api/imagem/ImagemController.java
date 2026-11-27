package br.com.xepamais.api.imagem;

import br.com.xepamais.api.auth.ContaInterceptor;
import br.com.xepamais.api.database.model.Imagem;
import br.com.xepamais.api.database.repository.ContaRepository;
import br.com.xepamais.api.database.repository.ImagemRepository;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Fotos das xepas e dos perfis, guardadas no banco (valem em qualquer aparelho).

@RestController
@io.swagger.v3.oas.annotations.tags.Tag(name = "Imagens", description = "Envio e leitura de fotos")
@RequestMapping("/api/imagens")
public class ImagemController {
    public static final String PREFIXO_URL = "/api/imagens/";
    private static final int TAMANHO_MAXIMO = 2 * 1024 * 1024;
    private static final List<String> TIPOS = List.of("image/jpeg", "image/png", "image/webp");
    private static final Pattern DATA_URL = Pattern.compile("^data:(image/[a-z]+);base64,(.+)$", Pattern.DOTALL);

    private final ImagemRepository imagemRepository;
    private final ContaRepository contaRepository;

    public ImagemController(ImagemRepository imagemRepository, ContaRepository contaRepository) {
        this.imagemRepository = imagemRepository;
        this.contaRepository = contaRepository;
    }

    public record EnvioImagem(String dataUrl) {}

    public record ImagemSalva(Integer id, String url) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ImagemSalva enviar(@RequestAttribute(ContaInterceptor.ATRIBUTO_CONTA) Integer idConta,
                              @RequestBody EnvioImagem envio) {
        Matcher partes = envio == null || envio.dataUrl() == null ? null : DATA_URL.matcher(envio.dataUrl().trim());
        if (partes == null || !partes.matches() || !TIPOS.contains(partes.group(1))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Envie uma imagem JPG, PNG ou WEBP.");
        }
        byte[] dados;
        try {
            dados = Base64.getMimeDecoder().decode(partes.group(2));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Imagem inválida.");
        }
        if (dados.length == 0 || dados.length > TAMANHO_MAXIMO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A imagem deve ter no máximo 2 MB.");
        }

        Imagem imagem = new Imagem();
        imagem.setConta(contaRepository.getReferenceById(idConta));
        imagem.setTipoConteudo(partes.group(1));
        imagem.setDados(dados);
        imagemRepository.save(imagem);
        return new ImagemSalva(imagem.getId(), PREFIXO_URL + imagem.getId());
    }

    // Pública (as fotos aparecem para todos). Nunca muda: o navegador pode guardar por 1 ano.
    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> ver(@PathVariable Integer id) {
        Imagem imagem = imagemRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Imagem não encontrada."));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(imagem.getTipoConteudo()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .body(imagem.getDados());
    }

    // Aceita só endereços de foto: os da própria API ou http(s).
    public static String enderecoValido(String endereco) {
        if (endereco == null) return null;
        String limpo = endereco.trim();
        if (limpo.isEmpty() || limpo.length() > 255) return null;
        if (limpo.matches("^/api/imagens/\\d+$") || limpo.matches("^https?://\\S+$")) return limpo;
        return null;
    }
}
