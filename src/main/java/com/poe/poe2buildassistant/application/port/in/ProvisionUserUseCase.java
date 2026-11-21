package com.poe.poe2buildassistant.application.port.in;

import com.poe.poe2buildassistant.domain.user.User;

/** Cria ou atualiza a conta a cada login com o Google e decide o papel. */
public interface ProvisionUserUseCase {

    /** O que o Google confirma sobre quem entrou; o adaptador converte do OIDC para isto. */
    record GoogleIdentity(String sub, String email, boolean emailVerified, String name) {}

    record Provisioned(User user, boolean firstLogin) {}

    Provisioned provision(GoogleIdentity identity);
}
