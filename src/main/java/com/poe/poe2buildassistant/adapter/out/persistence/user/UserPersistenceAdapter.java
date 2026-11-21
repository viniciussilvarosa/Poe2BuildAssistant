package com.poe.poe2buildassistant.adapter.out.persistence.user;

import com.poe.poe2buildassistant.application.port.out.UserRepositoryPort;
import com.poe.poe2buildassistant.domain.user.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class UserPersistenceAdapter implements UserRepositoryPort {

    private final JdbcClient jdbc;

    UserPersistenceAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByGoogleSub(String googleSub) {
        return jdbc.sql("SELECT id, google_sub, email, display_name, role FROM app_user WHERE google_sub = ?")
                .param(googleSub)
                .query((rs, n) -> new User(
                        rs.getObject("id", UUID.class),
                        rs.getString("google_sub"),
                        rs.getString("email"),
                        rs.getString("display_name"),
                        User.Role.valueOf(rs.getString("role"))))
                .optional();
    }

    /**
     * Upsert pelo sub do Google: dois logins simultâneos da mesma conta nova (duas abas) caem na
     * mesma linha, e o id devolvido é sempre o que ficou no banco (chaos L6).
     */
    @Override
    @Transactional
    public User save(User user) {
        UUID id = jdbc.sql("""
                        INSERT INTO app_user (id, google_sub, email, display_name, role)
                        VALUES (?, ?, ?, ?, ?)
                        ON CONFLICT (google_sub) DO UPDATE
                        SET email = EXCLUDED.email, display_name = EXCLUDED.display_name,
                            role = EXCLUDED.role, last_login_at = now()
                        RETURNING id
                        """)
                .params(user.id(), user.googleSub(), user.email(), user.displayName(), user.role().name())
                .query(UUID.class)
                .single();
        return new User(id, user.googleSub(), user.email(), user.displayName(), user.role());
    }
}
