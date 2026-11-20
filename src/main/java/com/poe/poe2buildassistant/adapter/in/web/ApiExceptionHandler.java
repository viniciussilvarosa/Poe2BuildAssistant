package com.poe.poe2buildassistant.adapter.in.web;

import com.poe.poe2buildassistant.domain.shared.DomainException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Erros síncronos da API (antes de abrir o stream) como ProblemDetail em pt-BR. */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ProblemDetail> domain(DomainException e) {
        ProblemDetail problem = ApiProblems.from(e);
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }
}
