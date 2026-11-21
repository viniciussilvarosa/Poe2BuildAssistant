package com.poe.poe2buildassistant.adapter.in.web;

import com.poe.poe2buildassistant.domain.shared.DomainException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Erros síncronos da API (antes de abrir o stream) como ProblemDetail em pt-BR. */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ProblemDetail> domain(DomainException e) {
        ProblemDetail problem = ApiProblems.from(e);
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }

    /**
     * Qualquer outra falha (banco fora, bug) vira 500 em pt-BR. Sem isto, ela subia para o
     * despacho de /error, que o SecurityConfig nega, e o cliente recebia um 403 vazio (chaos O8).
     * As exceções do Spring MVC (405, 404, corpo inválido...) continuam nos handlers da classe-mãe,
     * que são mais específicos.
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception e) {
        ProblemDetail problem = ApiProblems.from(e);
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }

    /** JSON vazio, malformado ou com tipos errados: mensagem em pt-BR, como o resto da API (chaos X3). */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // A dica do campo concept só faz sentido no formulário de build; o admin recebe a genérica (chaos C6).
        boolean buildForm = request instanceof ServletWebRequest web && web.getRequest().getRequestURI().startsWith("/api/builds/generate");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                buildForm
                        ? "O corpo da requisição é inválido. Envie um JSON com o campo concept."
                        : "O corpo da requisição não é um JSON válido para esta operação.");
        problem.setTitle("Requisição inválida");
        return ResponseEntity.badRequest().body(problem);
    }
}
