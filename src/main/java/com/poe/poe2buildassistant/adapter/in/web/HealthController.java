package com.poe.poe2buildassistant.adapter.in.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Verificação simples de disponibilidade da API. */
@RestController
@RequestMapping("/api/health")
class HealthController {

    record HealthResponse(String status) {}

    private static final HealthResponse OK = new HealthResponse("ok");

    @GetMapping
    HealthResponse health() {
        return OK;
    }
}
