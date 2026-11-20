package com.poe.poe2buildassistant.adapter.in.web;

import com.poe.poe2buildassistant.domain.quota.Requester;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Descobre quem está pedindo. Por enquanto só visitantes (o login Google entra na #14): o IP vem
 * de {@code getRemoteAddr()}, que em produção já reflete o {@code X-Forwarded-For} do proxy
 * confiável ({@code server.forward-headers-strategy}). O IP vira hash na hora e não é guardado.
 */
public class RequesterResolver {

    private final String salt;

    public RequesterResolver(String salt) {
        this.salt = salt;
    }

    public Requester resolve(HttpServletRequest request) {
        return Requester.anonymousFromIp(request.getRemoteAddr(), salt);
    }
}
