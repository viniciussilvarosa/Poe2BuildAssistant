package com.poe.poe2buildassistant.application.service;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** @param adminEmails e-mails que recebem o papel ADMIN no login (comparação sem caixa) */
public record AccountSettings(Set<String> adminEmails) {

    public AccountSettings {
        adminEmails = adminEmails == null
                ? Set.of()
                : adminEmails.stream()
                        .filter(e -> e != null && !e.isBlank())
                        .map(e -> e.strip().toLowerCase(Locale.ROOT))
                        .collect(Collectors.toUnmodifiableSet());
    }

    boolean isAdmin(String email) {
        return email != null && adminEmails.contains(email.strip().toLowerCase(Locale.ROOT));
    }
}
