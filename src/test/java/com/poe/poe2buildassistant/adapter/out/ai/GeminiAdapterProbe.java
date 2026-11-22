package com.poe.poe2buildassistant.adapter.out.ai;

import com.poe.poe2buildassistant.application.port.out.BuildGeneratorPort;
import com.poe.poe2buildassistant.application.port.out.ClockPort;
import java.time.Duration;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

/** Acesso de teste ao adaptador package-private, para os testes de caos de outro pacote. */
public final class GeminiAdapterProbe {

    private GeminiAdapterProbe() {}

    public static BuildGeneratorPort adapter(ChatModel model, String fallbackModel, Duration timeout, ClockPort clock) {
        return new GeminiBuildGeneratorAdapter(ChatClient.builder(model), clock, new AiProperties(timeout, List.of(fallbackModel)));
    }
}
