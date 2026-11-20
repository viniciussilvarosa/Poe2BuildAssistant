package com.poe.poe2buildassistant.application.port.out;

import java.time.Instant;

/** Hora atual, injetável para testes determinísticos (cota, retryAt). */
@FunctionalInterface
public interface ClockPort {

    Instant now();
}
