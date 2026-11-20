package com.poe.poe2buildassistant.chaos.archfixture.domain;

/** Fixture: domínio usando Spring (violação clássica). */
public class SpringInDomain {
    public void validar(Object o) {
        org.springframework.util.Assert.notNull(o, "x");
    }
}
