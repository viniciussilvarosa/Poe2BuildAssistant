package com.poe.poe2buildassistant.adapter.out.persistence.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.poe.poe2buildassistant.TestcontainersConfiguration;
import com.poe.poe2buildassistant.domain.user.User;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, UserPersistenceAdapter.class})
class UserPersistenceAdapterTest {

    @Autowired private UserPersistenceAdapter adapter;

    @Test
    @DisplayName("salva e acha pelo sub do Google; salvar de novo atualiza")
    void salvaEAtualiza() {
        UUID id = UUID.randomUUID();
        adapter.save(new User(id, "g-1", "ana@exemplo.test", "Ana", User.Role.USER));
        adapter.save(new User(id, "g-1", "ana@exemplo.test", "Ana Silva", User.Role.ADMIN));

        assertThat(adapter.findByGoogleSub("g-1")).hasValueSatisfying(u -> {
            assertThat(u.id()).isEqualTo(id);
            assertThat(u.displayName()).isEqualTo("Ana Silva");
            assertThat(u.role()).isEqualTo(User.Role.ADMIN);
        });
        assertThat(adapter.findByGoogleSub("g-outro")).isEmpty();
    }

    @Test
    @DisplayName("mesmo sub com outro id não cria segunda conta: atualiza a existente e devolve o id dela")
    void subUnico() {
        UUID original = UUID.randomUUID();
        adapter.save(new User(original, "g-1", "a@exemplo.test", "A", User.Role.USER));

        User saved = adapter.save(new User(UUID.randomUUID(), "g-1", "b@exemplo.test", "B", User.Role.USER));

        assertThat(saved.id()).isEqualTo(original);
        assertThat(adapter.findByGoogleSub("g-1")).hasValueSatisfying(u -> assertThat(u.displayName()).isEqualTo("B"));
    }
}
