package com.poe.poe2buildassistant.application.port.out;

import com.poe.poe2buildassistant.domain.quota.Requester;
import java.time.LocalDate;

/** Contador diário de gerações por {@link Requester}. */
public interface QuotaCounterPort {

    /**
     * Consome uma unidade se o uso do dia ainda estiver abaixo do limite. Verificação e incremento
     * são uma operação atômica: requisições simultâneas nunca ultrapassam o limite.
     *
     * @return {@code true} se consumiu; {@code false} se o limite já foi atingido
     */
    boolean tryConsume(Requester requester, LocalDate day, int limit);

    /** Devolve uma unidade (ex.: o provedor falhou sem culpa do usuário). Nunca fica negativo. */
    void release(Requester requester, LocalDate day);

    int current(Requester requester, LocalDate day);
}
