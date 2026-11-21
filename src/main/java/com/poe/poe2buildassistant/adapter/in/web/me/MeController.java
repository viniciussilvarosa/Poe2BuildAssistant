package com.poe.poe2buildassistant.adapter.in.web.me;

import com.poe.poe2buildassistant.adapter.in.web.auth.SessionUser;
import com.poe.poe2buildassistant.application.port.in.ListMyBuildsUseCase;
import com.poe.poe2buildassistant.domain.build.Build;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Quem está logado e o histórico dele. O SecurityConfig exige login em /api/me/**. */
@RestController
@RequestMapping("/api/me")
class MeController {

    /** Só nome e papel: e-mail e sub do Google ficam no servidor. */
    record MeResponse(String name, String role) {}

    record MyBuildItem(
            UUID id, Instant createdAt, String concept, String characterClass, String ascendancy, String mainSkill) {

        static MyBuildItem from(Build b) {
            return new MyBuildItem(
                    b.id(),
                    b.createdAt(),
                    b.request().concept(),
                    b.content().characterClass(),
                    b.content().ascendancy(),
                    b.content().mainSkill());
        }
    }

    private final ListMyBuildsUseCase listMyBuilds;

    MeController(ListMyBuildsUseCase listMyBuilds) {
        this.listMyBuilds = listMyBuilds;
    }

    @GetMapping
    MeResponse me(@AuthenticationPrincipal SessionUser user) {
        SessionUser me = required(user);
        return new MeResponse(me.displayName(), me.role());
    }

    @GetMapping("/builds")
    List<MyBuildItem> myBuilds(@AuthenticationPrincipal SessionUser user) {
        return listMyBuilds.list(required(user).userId()).stream().map(MyBuildItem::from).toList();
    }

    /** Sessão sem a conta do app (ex.: login sem o escopo openid) vale como visitante (chaos L5). */
    private static SessionUser required(SessionUser user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Entre com o Google para ver esta página.");
        }
        return user;
    }
}
