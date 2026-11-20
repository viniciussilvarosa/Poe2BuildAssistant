package com.poe.poe2buildassistant.adapter.out.ai;

import com.google.genai.errors.ApiException;
import com.poe.poe2buildassistant.application.port.out.BuildGeneratorPort;
import com.poe.poe2buildassistant.application.port.out.ClockPort;
import com.poe.poe2buildassistant.domain.build.BuildRequest;
import com.poe.poe2buildassistant.domain.build.BuildViolation;
import com.poe.poe2buildassistant.domain.build.CatalogSlice;
import com.poe.poe2buildassistant.domain.build.Constraints;
import com.poe.poe2buildassistant.domain.build.GeneratedBuildDraft;
import com.poe.poe2buildassistant.domain.catalog.SkillGem;
import com.poe.poe2buildassistant.domain.shared.GenerationFailed;
import com.poe.poe2buildassistant.domain.shared.ProviderUnavailable;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Gera rascunhos de build com o Google Gemini via Spring AI. Traduz falhas do provedor para
 * exceções de domínio e nunca devolve rascunho parcial. Só o conceito e as escolhas do jogador vão
 * no prompt: nenhum dado pessoal.
 */
@Component
class GeminiBuildGeneratorAdapter implements BuildGeneratorPort {

    private static final Logger log = LoggerFactory.getLogger(GeminiBuildGeneratorAdapter.class);
    /** Gemini informa a espera no corpo do erro 429, ex.: "retryDelay": "38s". */
    private static final Pattern RETRY_DELAY = Pattern.compile("retryDelay\"?\\s*[:=]\\s*\"?(\\d+)(?:\\.\\d+)?s");

    private final ChatClient client;
    private final ClockPort clock;
    private final AiProperties properties;
    private final BeanOutputConverter<GeneratedBuildResponse> converter =
            new BeanOutputConverter<>(GeneratedBuildResponse.class);
    private final String systemPrompt;
    private final PromptTemplate userTemplate;

    GeminiBuildGeneratorAdapter(ChatClient.Builder builder, ClockPort clock, AiProperties properties) {
        this.client = builder.build();
        this.clock = clock;
        this.properties = properties;
        this.systemPrompt = new PromptTemplate(new ClassPathResource("prompts/build-system.st")).render();
        this.userTemplate = new PromptTemplate(new ClassPathResource("prompts/build-user.st"));
    }

    @Override
    public GeneratedBuildDraft generate(BuildRequest request, CatalogSlice slice, List<BuildViolation> feedback) {
        Prompt prompt = new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(userPrompt(request, slice, feedback))));
        long start = System.nanoTime();
        ChatResponse response = call(prompt);
        var metadata = response.getMetadata();
        var usage = metadata == null ? null : metadata.getUsage();
        log.atInfo()
                .setMessage("ai.generation.call")
                .addKeyValue("model", metadata == null ? null : metadata.getModel())
                .addKeyValue("durationMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start))
                .addKeyValue("promptTokens", usage == null ? null : usage.getPromptTokens())
                .addKeyValue("completionTokens", usage == null ? null : usage.getCompletionTokens())
                .addKeyValue("feedbackViolations", feedback.size())
                .log();
        return toDraft(text(response));
    }

    private ChatResponse call(Prompt prompt) {
        try {
            // Virtual thread: a espera pela rede não ocupa o pool comum, e o timeout corta a espera
            // do usuário mesmo sem timeout configurável no cliente do Gemini.
            return CompletableFuture.supplyAsync(
                            () -> client.prompt(prompt).call().chatResponse(), r -> Thread.ofVirtual().start(r))
                    .get(properties.timeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.atWarn().setMessage("ai.provider.unavailable").addKeyValue("status", "timeout").log();
            throw new ProviderUnavailable(Optional.empty());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderUnavailable(Optional.empty());
        } catch (ExecutionException e) {
            throw translate(e.getCause());
        }
    }

    private RuntimeException translate(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (t instanceof ApiException api && (api.code() == 429 || api.code() >= 500 || api.code() == 401 || api.code() == 403)) {
                Optional<Instant> retryAt = api.code() == 429 ? retryDelay(api.message()) : Optional.empty();
                log.atWarn()
                        .setMessage("ai.provider.unavailable")
                        .addKeyValue("status", api.code())
                        .addKeyValue("retryAt", retryAt.orElse(null))
                        .log();
                return new ProviderUnavailable(retryAt);
            }
        }
        log.atError().setMessage("ai.generation.failed").setCause(failure).log();
        return new GenerationFailed();
    }

    private Optional<Instant> retryDelay(String message) {
        Matcher m = RETRY_DELAY.matcher(message == null ? "" : message);
        return m.find() ? Optional.of(clock.now().plusSeconds(Long.parseLong(m.group(1)))) : Optional.empty();
    }

    private static String text(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        String text = response.getResult().getOutput().getText();
        return text == null ? "" : text;
    }

    private GeneratedBuildDraft toDraft(String text) {
        GeneratedBuildResponse r;
        try {
            r = text.isBlank() ? null : converter.convert(text);
        } catch (RuntimeException e) {
            r = null;
        }
        if (r == null) {
            log.atError().setMessage("ai.generation.malformed").addKeyValue("responseLength", text.length()).log();
            throw new GenerationFailed();
        }
        return new GeneratedBuildDraft(
                r.summary(),
                r.synergy(),
                r.choiceReasons() == null ? List.of()
                        : r.choiceReasons().stream()
                                .filter(c -> c != null)
                                .map(c -> new GeneratedBuildDraft.ChoiceReason(c.field(), c.reason()))
                                .toList(),
                r.characterClass(),
                r.ascendancy(),
                r.weaponType(),
                r.mainSkill(),
                r.supports(),
                r.passiveTree() == null ? null : new GeneratedBuildDraft.PassiveDirection(r.passiveTree().direction(), r.passiveTree().notables()),
                r.gear() == null ? null : new GeneratedBuildDraft.GearPriorities(r.gear().weapon(), r.gear().armour(), r.gear().accessories()),
                r.campaignTips());
    }

    private String userPrompt(BuildRequest request, CatalogSlice slice, List<BuildViolation> feedback) {
        return userTemplate.render(Map.of(
                "concept", neutralize(request.concept()),
                "constraints", constraints(request.constraints()),
                "classes", names(slice.classes(), c -> c.name()),
                "ascendancies", names(slice.ascendancies(), a -> a.name() + " (" + a.characterClass().name() + ")"),
                "weaponTypes", names(slice.weaponTypes(), w -> w.name()),
                "skillGems", names(slice.skillGems(), GeminiBuildGeneratorAdapter::gem),
                "supportGems", names(slice.supportGems(), s -> s.name()),
                "notables", names(slice.notables(), n -> n.name()),
                "feedback", feedback(feedback),
                "format", converter.getFormat()));
    }

    /** Impede que o conceito feche a tag {@code <conceito>} e se passe por instrução. */
    private static String neutralize(String concept) {
        return concept.replace('<', '‹').replace('>', '›');
    }

    private static String constraints(Constraints c) {
        String fixed = Map.of(
                        "Classe", c.characterClass(),
                        "Ascendência", c.ascendancy(),
                        "Arma", c.weaponType(),
                        "Habilidade principal", c.mainSkill(),
                        "Estilo de jogo", c.playstyle())
                .entrySet().stream()
                .filter(e -> e.getValue().isPresent())
                .map(e -> "- " + e.getKey() + ": " + e.getValue().get())
                .sorted()
                .collect(Collectors.joining("\n"));
        return fixed.isEmpty() ? "- Nenhuma: escolha tudo de acordo com o conceito." : fixed;
    }

    private static String gem(SkillGem g) {
        return g.weaponTypes().isEmpty() ? g.name() : g.name() + " (armas: " + String.join("/", g.weaponTypes()) + ")";
    }

    private static <T> String names(List<T> items, Function<T, String> name) {
        return items.stream().map(name).collect(Collectors.joining(", "));
    }

    private static String feedback(List<BuildViolation> violations) {
        if (violations.isEmpty()) {
            return "";
        }
        return "\nA tentativa anterior foi recusada por estes problemas; corrija todos:\n"
                + violations.stream()
                        .map(v -> "- " + v.type() + " em " + v.field() + ": \"" + v.itemName() + "\"")
                        .collect(Collectors.joining("\n"))
                + "\n";
    }
}
