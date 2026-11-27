package br.com.xepamais.api.chat;

import br.com.xepamais.api.chat.ConversaService.ConversaResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Conversas entre consumidor e feirante. {papel} = consumidor ou feirante; {idConta} = id do login.
@RestController
@RequestMapping("/api/{papel:consumidor|feirante}/{idConta}/conversas")
public class ConversaController {
    private final ConversaService service;

    public ConversaController(ConversaService service) {
        this.service = service;
    }

    public record DuvidaRequest(Integer idXepa) {}

    public record MensagemRequest(String texto) {}

    @GetMapping
    public List<ConversaResponse> listar(@PathVariable String papel, @PathVariable Integer idConta) {
        return service.listar(papel, idConta);
    }

    @GetMapping("/{idChat}")
    public ConversaResponse buscar(@PathVariable String papel, @PathVariable Integer idConta,
                                   @PathVariable Integer idChat) {
        return service.buscar(papel, idConta, idChat);
    }

    // Só o consumidor abre dúvidas sobre uma xepa: { "idXepa": 3 }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConversaResponse abrirDuvida(@PathVariable String papel, @PathVariable Integer idConta,
                                        @RequestBody DuvidaRequest request) {
        if (!ConversaService.CONSUMIDOR.equals(papel)) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Só o consumidor abre conversas sobre xepas.");
        }
        return service.abrirDuvida(idConta, request == null ? null : request.idXepa());
    }

    @PostMapping("/{idChat}/mensagens")
    @ResponseStatus(HttpStatus.CREATED)
    public ConversaResponse enviar(@PathVariable String papel, @PathVariable Integer idConta,
                                   @PathVariable Integer idChat, @RequestBody MensagemRequest request) {
        return service.enviar(papel, idConta, idChat, request == null ? null : request.texto());
    }

    @PostMapping("/{idChat}/leitura")
    public ConversaResponse marcarComoLida(@PathVariable String papel, @PathVariable Integer idConta,
                                           @PathVariable Integer idChat) {
        return service.marcarComoLida(papel, idConta, idChat);
    }

    @PostMapping("/{idChat}/finalizar")
    public ConversaResponse finalizar(@PathVariable String papel, @PathVariable Integer idConta,
                                      @PathVariable Integer idChat) {
        if (!ConversaService.FEIRANTE.equals(papel)) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Só o feirante finaliza a conversa.");
        }
        return service.finalizar(idConta, idChat);
    }
}
