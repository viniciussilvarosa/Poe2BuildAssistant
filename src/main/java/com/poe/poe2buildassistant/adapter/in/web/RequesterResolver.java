package com.poe.poe2buildassistant.adapter.in.web;

import com.poe.poe2buildassistant.adapter.in.web.auth.SessionUser;
import com.poe.poe2buildassistant.domain.quota.Requester;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;

/**
 * Descobre quem está pedindo: com sessão do Google, o usuário pelo id interno; sem sessão, o
 * visitante pelo hash do IP. O IP vem de {@code getRemoteAddr()}, que em produção já reflete o
 * {@code X-Forwarded-For} do proxy confiável ({@code server.forward-headers-strategy}). O IP vira hash na hora e não é guardado.
 */
public class RequesterResolver {

    private static final Logger log = LoggerFactory.getLogger(RequesterResolver.class);

    /**
     * Endereço "não especificado" do IPv6: nunca é o IP de um cliente real, então serve de chave
     * única para quem chega sem IP interpretável (proxy mandando "unknown", lixo no cabeçalho).
     * Todos esses dividem uma única cota, o que é conservador e não abre brecha (chaos X1).
     */
    private static final String UNKNOWN_ORIGIN = "::";

    private final String salt;

    public RequesterResolver(String salt) {
        this.salt = salt;
    }

    public Requester resolve(HttpServletRequest request) {
        if (request.getUserPrincipal() instanceof Authentication auth && auth.getPrincipal() instanceof SessionUser user) {
            return new Requester.Authenticated(user.userId());
        }
        String address = withoutZone(request.getRemoteAddr());
        try {
            return Requester.anonymousFromIp(address, salt);
        } catch (IllegalArgumentException e) {
            log.atWarn().setMessage("requester.unknown_origin").log();
            return Requester.anonymousFromIp(UNKNOWN_ORIGIN, salt);
        }
    }

    /** IPv6 link-local chega com zona ("fe80::1%eth0"); a zona não identifica o cliente. */
    private static String withoutZone(String address) {
        if (address == null) {
            return null;
        }
        int zone = address.indexOf('%');
        return zone < 0 ? address : address.substring(0, zone);
    }
}
