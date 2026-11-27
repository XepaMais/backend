package br.com.xepamais.api.xepinha;

import br.com.xepamais.api.xepinha.XepinhaService.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Xepinha: assistente com IA dos feirantes
@RestController
@io.swagger.v3.oas.annotations.tags.Tag(name = "Xepinha (IA)", description = "Assistente com IA dos feirantes")
@RequestMapping("/api")
public class XepinhaController {
    private final XepinhaService service;

    public XepinhaController(XepinhaService service) {
        this.service = service;
    }

    // Conversas salvas do feirante
    @GetMapping("/feirante/{idConta}/xepinha/conversas")
    public List<ConversaResponse> conversas(@PathVariable Integer idConta) {
        return service.conversas(idConta);
    }

    // { "idConversa": 3, "texto": "Como registro uma despesa?" }
    @PostMapping("/feirante/{idConta}/xepinha/mensagens")
    public ConversaResponse perguntar(@PathVariable Integer idConta, @RequestBody PerguntaRequest request) {
        return service.perguntar(idConta, request);
    }

    // Modo demonstração: nada é salvo e há limite de perguntas por IP
    @PostMapping("/xepinha/demo")
    public RespostaDemo perguntarDemo(@RequestBody PerguntaDemoRequest request, HttpServletRequest http) {
        return service.perguntarDemo(request, ip(http));
    }

    private static String ip(HttpServletRequest http) {
        String encaminhado = http.getHeader("X-Forwarded-For");
        if (encaminhado != null && !encaminhado.isBlank()) return encaminhado.split(",")[0].trim();
        return http.getRemoteAddr();
    }
}
