package com.poe.poe2buildassistant.adapter.in.web.build;

import com.poe.poe2buildassistant.application.port.out.GenerationProgressPort;
import com.poe.poe2buildassistant.domain.build.GenerationStep;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Progresso de UMA geração enviado como Server-Sent Events. O percentual nunca volta atrás, mesmo
 * quando a geração passa de novo por GENERATING numa regeneração.
 */
final class SseProgress implements GenerationProgressPort {

    record ProgressEvent(GenerationStep step, int percent) {}

    private static final Map<GenerationStep, Integer> PERCENT = Map.of(
            GenerationStep.VALIDATING_INPUT, 10,
            GenerationStep.GENERATING, 40,
            GenerationStep.VALIDATING_OUTPUT, 80,
            GenerationStep.SAVING, 95,
            GenerationStep.DONE, 100);

    private final SseEmitter emitter;
    private int percent;

    SseProgress(SseEmitter emitter) {
        this.emitter = emitter;
    }

    @Override
    public synchronized void emit(GenerationStep step) {
        percent = Math.max(percent, PERCENT.get(step));
        send("progress", new ProgressEvent(step, percent));
    }

    /** @throws UncheckedIOException se o cliente já desconectou */
    void send(String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
