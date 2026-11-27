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

// Todos os erros da API saem no mesmo formato
@RestControllerAdvice
public class TratadorErros {
    private static final Logger log = LoggerFactory.getLogger(TratadorErros.class);
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    public record Erro(int status, String erro, String message, String caminho, LocalDateTime momento) {}

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Erro> regraDeNegocio(ResponseStatusException ex, HttpServletRequest req) {
        String motivo = ex.getReason();
        if (motivo == null) {
            motivo = padrao(ex.getStatusCode());
        }
        return resposta(ex.getStatusCode(), motivo, req);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Erro> jsonInvalido(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return resposta(HttpStatus.BAD_REQUEST, "Dados enviados em formato inválido. Confira os campos e tente de novo.", req);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<Erro> parametroInvalido(Exception ex, HttpServletRequest req) {
        String nome;
        if (ex instanceof MethodArgumentTypeMismatchException) {
            nome = ((MethodArgumentTypeMismatchException) ex).getName();
        } else {
            nome = ((MissingServletRequestParameterException) ex).getParameterName();
        }
        return resposta(HttpStatus.BAD_REQUEST, "Parâmetro inválido ou ausente: " + nome + ".", req);
    }

    // @Version da xepa: duas pessoas compraram a mesma unidade ao mesmo tempo
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Erro> concorrencia(ObjectOptimisticLockingFailureException ex, HttpServletRequest req) {
        return resposta(HttpStatus.CONFLICT, "Este item acabou de ser alterado por outra pessoa. Atualize a página e tente de novo.", req);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Erro> integridade(DataIntegrityViolationException ex, HttpServletRequest req) {
        log.warn("Violação de integridade em {}: {}", req.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return resposta(HttpStatus.CONFLICT, "Não foi possível salvar: os dados conflitam com um registro existente.", req);
    }

    // Erros do próprio Spring e qualquer outro
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Erro> inesperado(Exception ex, HttpServletRequest req) {
        if (ex instanceof ErrorResponse) {
            HttpStatusCode status = ((ErrorResponse) ex).getStatusCode();
            return resposta(status, padrao(status), req);
        }
        log.error("Erro inesperado em {} {}", req.getMethod(), req.getRequestURI(), ex);
        return resposta(HttpStatus.INTERNAL_SERVER_ERROR, padrao(HttpStatus.INTERNAL_SERVER_ERROR), req);
    }

    private static ResponseEntity<Erro> resposta(HttpStatusCode status, String mensagem, HttpServletRequest req) {
        HttpStatus conhecido = HttpStatus.resolve(status.value());
        String nome = String.valueOf(status.value());
        if (conhecido != null) {
            nome = conhecido.getReasonPhrase();
        }
        Erro erro = new Erro(status.value(), nome, mensagem, req.getRequestURI(), LocalDateTime.now(FUSO));
        return ResponseEntity.status(status).body(erro);
    }

    // Mensagem padrão para cada código de erro
    private static String padrao(HttpStatusCode status) {
        int codigo = status.value();
        if (codigo == 400) {
            return "Requisição inválida.";
        } else if (codigo == 401) {
            return "Sessão expirada. Entre novamente.";
        } else if (codigo == 403) {
            return "Você não tem acesso a este recurso.";
        } else if (codigo == 404) {
            return "Recurso não encontrado.";
        } else if (codigo == 405) {
            return "Método não permitido para esta rota.";
        } else if (codigo == 409) {
            return "Conflito com o estado atual do recurso.";
        } else if (codigo == 415) {
            return "Tipo de conteúdo não suportado. Envie JSON.";
        } else if (codigo == 429) {
            return "Muitas tentativas. Aguarde alguns minutos.";
        } else if (codigo == 503) {
            return "Serviço temporariamente indisponível. Tente de novo em instantes.";
        } else if (status.is5xxServerError()) {
            return "Erro interno no servidor. Tente de novo em instantes.";
        } else {
            return "Não foi possível concluir a operação.";
        }
    }
}
