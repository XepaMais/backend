package br.com.xepamais.api.config;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneId;

// Todos os erros da API saem no mesmo formato:
// { "status": 400, "erro": "Bad Request", "message": "Informe email e senha.", "caminho": "/api/auth/login", "momento": "..." }
// "message" é o texto que o front mostra ao usuário. Erros inesperados nunca expõem detalhes internos.
@RestControllerAdvice
public class TratadorErros {
    private static final Logger log = LoggerFactory.getLogger(TratadorErros.class);
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    public record Erro(int status, String erro, String message, String caminho, LocalDateTime momento) {}

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Erro> regraDeNegocio(ResponseStatusException ex, HttpServletRequest req) {
        String motivo = ex.getReason() != null ? ex.getReason() : padrao(ex.getStatusCode());
        return resposta(ex.getStatusCode(), motivo, req);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Erro> jsonInvalido(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return resposta(HttpStatus.BAD_REQUEST, "Dados enviados em formato inválido. Confira os campos e tente de novo.", req);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<Erro> parametroInvalido(Exception ex, HttpServletRequest req) {
        String nome = ex instanceof MethodArgumentTypeMismatchException m ? m.getName()
                : ((MissingServletRequestParameterException) ex).getParameterName();
        return resposta(HttpStatus.BAD_REQUEST, "Parâmetro inválido ou ausente: " + nome + ".", req);
    }

    // @Version da xepa: duas pessoas compraram a mesma unidade ao mesmo tempo.
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Erro> concorrencia(ObjectOptimisticLockingFailureException ex, HttpServletRequest req) {
        return resposta(HttpStatus.CONFLICT, "Este item acabou de ser alterado por outra pessoa. Atualize a página e tente de novo.", req);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Erro> integridade(DataIntegrityViolationException ex, HttpServletRequest req) {
        log.warn("Violação de integridade em {}: {}", req.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return resposta(HttpStatus.CONFLICT, "Não foi possível salvar: os dados conflitam com um registro existente.", req);
    }

    // Erros do próprio Spring (rota inexistente, método errado, tipo de conteúdo...) e qualquer outro.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Erro> inesperado(Exception ex, HttpServletRequest req) {
        if (ex instanceof ErrorResponse conhecido) {
            return resposta(conhecido.getStatusCode(), padrao(conhecido.getStatusCode()), req);
        }
        log.error("Erro inesperado em {} {}", req.getMethod(), req.getRequestURI(), ex);
        return resposta(HttpStatus.INTERNAL_SERVER_ERROR, padrao(HttpStatus.INTERNAL_SERVER_ERROR), req);
    }

    private static ResponseEntity<Erro> resposta(HttpStatusCode status, String mensagem, HttpServletRequest req) {
        HttpStatus conhecido = HttpStatus.resolve(status.value());
        String nome = conhecido != null ? conhecido.getReasonPhrase() : String.valueOf(status.value());
        return ResponseEntity.status(status)
                .body(new Erro(status.value(), nome, mensagem, req.getRequestURI(), LocalDateTime.now(FUSO)));
    }

    private static String padrao(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "Requisição inválida.";
            case 401 -> "Sessão expirada. Entre novamente.";
            case 403 -> "Você não tem acesso a este recurso.";
            case 404 -> "Recurso não encontrado.";
            case 405 -> "Método não permitido para esta rota.";
            case 409 -> "Conflito com o estado atual do recurso.";
            case 415 -> "Tipo de conteúdo não suportado. Envie JSON.";
            case 429 -> "Muitas tentativas. Aguarde alguns minutos.";
            case 503 -> "Serviço temporariamente indisponível. Tente de novo em instantes.";
            default -> status.is5xxServerError()
                    ? "Erro interno no servidor. Tente de novo em instantes."
                    : "Não foi possível concluir a operação.";
        };
    }
}
