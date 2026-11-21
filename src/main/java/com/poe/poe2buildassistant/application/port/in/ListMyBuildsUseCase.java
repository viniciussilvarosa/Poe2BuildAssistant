package com.poe.poe2buildassistant.application.port.in;

import com.poe.poe2buildassistant.domain.build.Build;
import java.util.List;

/** "Minhas builds": histórico de quem está logado, do mais recente para o mais antigo. */
public interface ListMyBuildsUseCase {

    List<Build> list(String userId);
}
