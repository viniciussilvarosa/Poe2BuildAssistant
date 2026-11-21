package com.poe.poe2buildassistant.application.port.out;

import com.poe.poe2buildassistant.domain.user.User;
import java.util.Optional;

public interface UserRepositoryPort {

    Optional<User> findByGoogleSub(String googleSub);

    /** Cria ou atualiza pelo id; o sub do Google é único. */
    User save(User user);
}
