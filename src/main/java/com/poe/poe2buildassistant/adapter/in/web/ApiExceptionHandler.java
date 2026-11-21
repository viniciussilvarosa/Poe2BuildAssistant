package com.poe.poe2buildassistant.adapter.in.web;

import com.poe.poe2buildassistant.domain.shared.DomainException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Erros síncronos da API (antes de abrir o stream) como ProblemDetail em pt-BR. */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Resource SPA_INDEX = new ClassPathResource("static/index.html");

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

    /**
     * Parâmetro de URL no formato errado (ex.: id de build que não é UUID): 400 em pt-BR, sem
     * repetir a entrada nem o tipo Java que o Spring descreveria em inglês (chaos R3).
     */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "O endereço não identifica um recurso válido. Confira o link.");
        problem.setTitle("Requisição inválida");
        return ResponseEntity.badRequest().body(problem);
    }

    /**
     * Nada encontrado para o caminho. Na API e em /assets continua 404 em JSON; numa navegação (um
     * link ou URL digitada), devolve o SPA com status 404 para o React mostrar "Página não
     * encontrada" com o menu, em vez de uma tela branca (bug #48).
     */
    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String path = request instanceof ServletWebRequest web ? web.getRequest().getRequestURI() : "";
        if (!path.startsWith("/api/") && !path.startsWith("/assets/") && SPA_INDEX.exists()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.TEXT_HTML).body(SPA_INDEX);
        }
        return super.handleNoResourceFoundException(ex, headers, status, request);
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
