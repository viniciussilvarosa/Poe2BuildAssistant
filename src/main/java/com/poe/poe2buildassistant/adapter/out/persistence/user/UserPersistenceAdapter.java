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

    @Override
    @Transactional
    public User save(User user) {
        jdbc.sql("""
                        INSERT INTO app_user (id, google_sub, email, display_name, role)
                        VALUES (?, ?, ?, ?, ?)
                        ON CONFLICT (id) DO UPDATE
                        SET email = EXCLUDED.email, display_name = EXCLUDED.display_name,
                            role = EXCLUDED.role, last_login_at = now()
                        """)
                .params(user.id(), user.googleSub(), user.email(), user.displayName(), user.role().name())
                .update();
        return user;
    }
}
