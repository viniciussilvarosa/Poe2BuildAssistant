package com.poe.poe2buildassistant.application.service;

import com.poe.poe2buildassistant.application.port.in.ProvisionUserUseCase;
import com.poe.poe2buildassistant.application.port.out.UserRepositoryPort;
import com.poe.poe2buildassistant.domain.user.User;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Um registro por sub do Google. O papel é recalculado a cada login: entrar ou sair da lista de
 * admins vale a partir do próximo login. Só e-mail verificado pelo Google conta para ADMIN.
 */
@Service
public class ProvisionUserService implements ProvisionUserUseCase {

    private static final Logger log = LoggerFactory.getLogger(ProvisionUserService.class);

    private final UserRepositoryPort users;
    private final AccountSettings settings;

    public ProvisionUserService(UserRepositoryPort users, AccountSettings settings) {
        this.users = users;
        this.settings = settings;
    }

    @Override
    @Transactional
    public Provisioned provision(GoogleIdentity identity) {
        Optional<User> existing = users.findByGoogleSub(identity.sub());
        User.Role role = identity.emailVerified() && settings.isAdmin(identity.email()) ? User.Role.ADMIN : User.Role.USER;
        UUID id = existing.map(User::id).orElseGet(UUID::randomUUID);
        User user = users.save(new User(id, identity.sub(), identity.email(), identity.name(), role));

        // Só o id vai para o log: e-mail e nome são dados pessoais.
        log.atInfo()
                .setMessage("auth.login.succeeded")
                .addKeyValue("userId", id)
                .addKeyValue("firstLogin", existing.isEmpty())
                .log();
        if (role == User.Role.ADMIN && existing.map(u -> u.role() != User.Role.ADMIN).orElse(true)) {
            log.atInfo().setMessage("auth.admin.granted").addKeyValue("userId", id).log();
        }
        return new Provisioned(user, existing.isEmpty());
    }
}
