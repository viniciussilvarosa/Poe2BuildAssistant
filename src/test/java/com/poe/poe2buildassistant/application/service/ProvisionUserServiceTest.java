package com.poe.poe2buildassistant.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.poe.poe2buildassistant.application.port.in.ProvisionUserUseCase.GoogleIdentity;
import com.poe.poe2buildassistant.application.port.in.ProvisionUserUseCase.Provisioned;
import com.poe.poe2buildassistant.application.port.out.UserRepositoryPort;
import com.poe.poe2buildassistant.domain.user.User;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProvisionUserServiceTest {

    /** Repositório em memória, indexado pelo sub do Google. */
    static final class FakeUsers implements UserRepositoryPort {
        final Map<String, User> bySub = new HashMap<>();

        @Override
        public Optional<User> findByGoogleSub(String googleSub) {
            return Optional.ofNullable(bySub.get(googleSub));
        }

        @Override
        public User save(User user) {
            bySub.put(user.googleSub(), user);
            return user;
        }
    }

    private final FakeUsers users = new FakeUsers();
    private final ProvisionUserService service =
            new ProvisionUserService(users, new AccountSettings(Set.of("Admin@Exemplo.test")));

    private static GoogleIdentity ana() {
        return new GoogleIdentity("g-1", "ana@exemplo.test", true, "Ana");
    }

    @Test
    @DisplayName("primeiro login cria usuário; segundo login não duplica")
    void naoDuplica() {
        Provisioned first = service.provision(ana());
        Provisioned second = service.provision(new GoogleIdentity("g-1", "ana@exemplo.test", true, "Ana Silva"));

        assertThat(users.bySub).hasSize(1);
        assertThat(first.firstLogin()).isTrue();
        assertThat(second.firstLogin()).isFalse();
        assertThat(second.user().id()).isEqualTo(first.user().id());
        assertThat(second.user().displayName()).isEqualTo("Ana Silva");
    }

    @Test
    @DisplayName("usuário comum recebe papel USER")
    void papelUser() {
        assertThat(service.provision(ana()).user().role()).isEqualTo(User.Role.USER);
    }

    @Test
    @DisplayName("e-mail na lista de admins recebe papel ADMIN (sem diferenciar caixa)")
    void papelAdmin() {
        User admin = service.provision(new GoogleIdentity("g-2", "admin@exemplo.test", true, "Admin")).user();

        assertThat(admin.role()).isEqualTo(User.Role.ADMIN);
    }

    @Test
    @DisplayName("e-mail de admin sem verificação no Google não vira ADMIN")
    void adminSemVerificacao() {
        User user = service.provision(new GoogleIdentity("g-3", "admin@exemplo.test", false, "Impostor")).user();

        assertThat(user.role()).isEqualTo(User.Role.USER);
    }

    @Test
    @DisplayName("quem sai da lista de admins volta a USER no próximo login")
    void perdeAdmin() {
        service.provision(new GoogleIdentity("g-2", "admin@exemplo.test", true, "Admin"));
        var semLista = new ProvisionUserService(users, new AccountSettings(Set.of()));

        assertThat(semLista.provision(new GoogleIdentity("g-2", "admin@exemplo.test", true, "Admin")).user().role())
                .isEqualTo(User.Role.USER);
    }
}
