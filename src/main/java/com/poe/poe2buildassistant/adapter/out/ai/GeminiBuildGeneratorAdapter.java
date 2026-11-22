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
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.FutureTask;
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
import org.springframework.ai.chat.prompt.ChatOptions;
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
    private static final Pattern RETRY_DELAY = Pattern.compile("retryDelay\"?\\s*[:=]\\s*\"?(\\d+(?:\\.\\d+)?)s");
    /** Teto para um retryDelay absurdo vindo do provedor (chaos G1). */
    private static final long MAX_RETRY_DELAY_SECONDS = Duration.ofHours(24).toSeconds();
    /** Qualquer sequência de espaço Unicode, inclusive quebras de linha (chaos G6). */
    private static final Pattern WHITESPACE = Pattern.compile("(?U)\\s+");

    private final ChatClient client;
    private final ClockPort clock;
    private final AiProperties properties;
    private final BeanOutputConverter<GeneratedBuildResponse> converter =
            new BeanOutputConverter<>(GeneratedBuildResponse.class);
    private final String systemPrompt;
    private final PromptTemplate userTemplate;
    /** Chave do disjuntor do modelo principal (o configurado em spring.ai.google.genai.chat.options.model). */
    private static final String PRIMARY = "primary";
    /**
     * Até quando cada modelo mandou esperar (429 com retryDelay): antes disso, nem é chamado (#64).
     * Por modelo (#89, caos C1): a cota esgotada do reserva não pode bloquear o principal.
     */
    private final Map<String, Instant> blockedUntil = new ConcurrentHashMap<>();

    GeminiBuildGeneratorAdapter(ChatClient.Builder builder, ClockPort clock, AiProperties properties) {
        this.client = builder.build();
        this.clock = clock;
        this.properties = properties;
        this.systemPrompt = new PromptTemplate(new ClassPathResource("prompts/build-system.st")).render();
        this.userTemplate = new PromptTemplate(new ClassPathResource("prompts/build-user.st"));
    }

    @Override
    public GeneratedBuildDraft generate(BuildRequest request, CatalogSlice slice, List<BuildViolation> feedback) {
        Instant until = blockedUntil.getOrDefault(PRIMARY, Instant.MIN);
        if (clock.now().isBefore(until)) {
            log.atInfo().setMessage("ai.provider.short_circuit").addKeyValue("retryAt", until).log();
            throw new ProviderUnavailable(Optional.of(until));
        }
        Prompt prompt = new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(userPrompt(request, slice, feedback))));
        long start = System.nanoTime();
        ChatResponse response = callWithFallback(prompt);
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

    /**
     * Chama o modelo principal e, se ele estiver sobrecarregado (5xx) ou estourar o timeout, repete
     * a mesma chamada no modelo reserva ({@code app.ai.fallback-model}), dentro da mesma tentativa
     * (#88). Cota (429), configuração e resposta malformada não trocam de modelo.
     */
    private ChatResponse callWithFallback(Prompt prompt) {
        try {
            return call(prompt, PRIMARY);
        } catch (Overloaded primary) {
            String fallback = properties.fallbackModel();
            if (fallback == null || fallback.isBlank() || isBlocked(fallback)) {
                // Sem reserva, ou reserva ainda dentro do 429 dele: termina sem gastar outra chamada.
                throw primary.unavailable;
            }
            log.atWarn()
                    .setMessage("ai.provider.fallback")
                    .addKeyValue("from", "primary")
                    .addKeyValue("to", fallback)
                    .addKeyValue("reason", primary.reason)
                    .log();
            try {
                return call(new Prompt(prompt.getInstructions(), ChatOptions.builder().model(fallback).build()), fallback);
            } catch (Overloaded | ProviderUnavailable fallbackDown) {
                // O erro é o do principal (sobrecarga, sem horário): o retryAt de um 429 do reserva não
                // vale para o principal e já ficou no disjuntor do reserva.
                throw primary.unavailable;
            }
        }
    }

    /** Provedor sobrecarregado ou lento: o único caso em que vale tentar o modelo reserva. */
    private static final class Overloaded extends RuntimeException {
        final ProviderUnavailable unavailable;
        final String reason;

        Overloaded(String reason) {
            super(reason, null, false, false);
            this.unavailable = new ProviderUnavailable(Optional.empty());
            this.reason = reason;
        }
    }

    private boolean isBlocked(String model) {
        return clock.now().isBefore(blockedUntil.getOrDefault(model, Instant.MIN));
    }

    /** @param breakerKey disjuntor que um 429 desta chamada alimenta: o do principal ou o do reserva */
    private ChatResponse call(Prompt prompt, String breakerKey) {
        // Virtual thread: a espera pela rede não ocupa o pool comum. O timeout corta a espera do
        // usuário e INTERROMPE a chamada, para ela não seguir gastando cota (G5); o cliente do
        // Gemini não tem timeout configurável.
        FutureTask<ChatResponse> task = new FutureTask<>(() -> client.prompt(prompt).call().chatResponse());
        Thread.ofVirtual().name("gemini-call").start(task);
        try {
            return task.get(properties.timeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            task.cancel(true);
            log.atWarn().setMessage("ai.provider.unavailable").addKeyValue("status", "timeout").log();
            throw new Overloaded("timeout");
        } catch (InterruptedException e) {
            task.cancel(true);
            Thread.currentThread().interrupt();
            throw new ProviderUnavailable(Optional.empty());
        } catch (ExecutionException e) {
            throw translate(e.getCause(), breakerKey);
        }
    }

    /**
     * Provedor indisponível (cota, fora do ar, rede, chave errada) vira {@link ProviderUnavailable}:
     * regenerar não resolve. Só o resto vira {@link GenerationFailed}.
     */
    private RuntimeException translate(Throwable failure, String breakerKey) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (t instanceof ApiException api) {
                if (isMisconfigured(api)) {
                    // Chave, permissão, modelo inexistente ou pré-condição: configuração, não pedido (G3, #44).
                    log.atError().setMessage("ai.provider.misconfigured").addKeyValue("status", api.code()).log();
                    return new ProviderUnavailable(Optional.empty());
                }
                if (api.code() == 429 || api.code() >= 500) {
                    Optional<Instant> retryAt = api.code() == 429 ? retryDelay(api.message()) : Optional.empty();
                    log.atWarn()
                            .setMessage("ai.provider.unavailable")
                            .addKeyValue("status", api.code())
                            .addKeyValue("retryAt", retryAt.orElse(null))
                            .log();
                    retryAt.ifPresent(at -> blockedUntil.put(breakerKey, at));
                    return api.code() >= 500 ? new Overloaded(String.valueOf(api.code())) : new ProviderUnavailable(retryAt);
                }
            }
            if (t instanceof IOException) {
                // Conexão recusada, DNS, socket: o provedor está inalcançável (G4).
                log.atWarn().setMessage("ai.provider.unavailable").addKeyValue("status", "network").log();
                return new ProviderUnavailable(Optional.empty());
            }
        }
        log.atError().setMessage("ai.generation.failed").setCause(failure).log();
        return new GenerationFailed();
    }

    /**
     * Erro de configuração do lado do servidor: regenerar não resolve e o usuário não tem culpa.
     * Inclui 404 (modelo descontinuado ou inexistente: todas as gerações caíam em "resposta
     * malformada", repetiam a chamada e não devolviam a cota, bug #44) e 400 FAILED_PRECONDITION
     * (região ou faturamento). Um 400 INVALID_ARGUMENT do pedido continua sendo falha de geração.
     */
    private static boolean isMisconfigured(ApiException api) {
        String message = api.message() == null ? "" : api.message().toLowerCase(Locale.ROOT);
        String status = api.status() == null ? "" : api.status();
        return api.code() == 401
                || api.code() == 403
                || api.code() == 404
                || (api.code() == 400 && (message.contains("api key") || "FAILED_PRECONDITION".equals(status)));
    }

    /**
     * Converte o retryDelay do Gemini ("38s", "37.6s") num horário. Arredonda para cima (nunca
     * libera antes da cota, G2) e limita a 24 h (valor absurdo não derruba nada, G1).
     */
    private Optional<Instant> retryDelay(String message) {
        Matcher m = RETRY_DELAY.matcher(message == null ? "" : message);
        if (!m.find()) {
            return Optional.empty();
        }
        BigDecimal seconds = new BigDecimal(m.group(1)).setScale(0, RoundingMode.CEILING);
        long capped = seconds.min(BigDecimal.valueOf(MAX_RETRY_DELAY_SECONDS)).longValueExact();
        return Optional.of(clock.now().plusSeconds(capped));
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

    /**
     * Texto não confiável (conceito do usuário, nomes vindos do LLM) vira uma linha só, sem como
     * fechar a tag {@code <conceito>}, forjar seções do prompt com quebras de linha ou fechar
     * aspas (G6).
     */
    private static String neutralize(String text) {
        return WHITESPACE.matcher(text).replaceAll(" ").strip().replace('<', '‹').replace('>', '›').replace('"', '\'');
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

    /**
     * Um nome por linha: há nomes com vírgula ("Morgana, the Storm Seer"), que numa lista separada
     * por vírgulas viravam dois nomes para a IA (QA-11). Custa ~1 token a mais por nome.
     */
    private static <T> String names(List<T> items, Function<T, String> name) {
        return items.stream().map(name).map(n -> "\n  - " + n).collect(Collectors.joining());
    }

    private static String feedback(List<BuildViolation> violations) {
        if (violations.isEmpty()) {
            return "";
        }
        return "\nA tentativa anterior foi recusada por estes problemas; corrija todos:\n"
                + violations.stream()
                        .map(v -> "- " + v.type() + " em " + v.field() + ": \"" + neutralize(v.itemName()) + "\"")
                        .collect(Collectors.joining("\n"))
                + "\n";
    }
}
