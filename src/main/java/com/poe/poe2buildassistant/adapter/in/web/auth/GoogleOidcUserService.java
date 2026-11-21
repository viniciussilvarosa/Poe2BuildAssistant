package com.poe.poe2buildassistant.adapter.in.web.auth;

import com.poe.poe2buildassistant.application.port.in.ProvisionUserUseCase;
import com.poe.poe2buildassistant.application.port.in.ProvisionUserUseCase.GoogleIdentity;
import com.poe.poe2buildassistant.application.port.in.ProvisionUserUseCase.Provisioned;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

/**
 * Converte o usuário OIDC do Google em conta do app no login. O Spring Security encontra este bean
 * pelo tipo e o usa no {@code oauth2Login}; a conversão para o domínio fica toda aqui.
 */
@Component
class GoogleOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final OidcUserService google = new OidcUserService();
    private final ProvisionUserUseCase provisioning;

    GoogleOidcUserService(ProvisionUserUseCase provisioning) {
        this.provisioning = provisioning;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest request) {
        OidcUser user = google.loadUser(request);
        Provisioned account = provisioning.provision(new GoogleIdentity(
                user.getSubject(), user.getEmail(), Boolean.TRUE.equals(user.getEmailVerified()), user.getFullName()));
        return new SessionUser(
                user.getIdToken(),
                account.user().id().toString(),
                account.user().displayName(),
                account.user().role().name());
    }
}
