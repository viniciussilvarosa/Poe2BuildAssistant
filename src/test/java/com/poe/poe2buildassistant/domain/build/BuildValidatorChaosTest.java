package com.poe.poe2buildassistant.domain.build;

import static com.poe.poe2buildassistant.domain.CatalogFixtures.FIVE_NOTABLES;
import static com.poe.poe2buildassistant.domain.CatalogFixtures.draft;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.tuple;

import com.poe.poe2buildassistant.domain.CatalogFixtures;
import com.poe.poe2buildassistant.domain.build.BuildViolation.Type;
import com.poe.poe2buildassistant.domain.build.GeneratedBuildDraft.ChoiceReason;
import com.poe.poe2buildassistant.domain.build.GeneratedBuildDraft.GearPriorities;
import com.poe.poe2buildassistant.domain.build.GeneratedBuildDraft.PassiveDirection;
import com.poe.poe2buildassistant.domain.catalog.Catalog;
import com.poe.poe2buildassistant.domain.catalog.CatalogVersion;
import com.poe.poe2buildassistant.domain.catalog.PassiveNotable;
import com.poe.poe2buildassistant.domain.catalog.SkillGem;
import com.poe.poe2buildassistant.domain.catalog.SupportGem;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Ataques ao validador de build e ao rascunho da IA (card #3). */
class BuildValidatorChaosTest {

    private final Catalog catalog = CatalogFixtures.catalog();
    private final BuildValidator validator = new BuildValidator();
    private final BuildRequest semRestricoes = new BuildRequest("arqueiro mágico", Constraints.none());

    private List<BuildViolation> validar(GeneratedBuildDraft draft) {
        return validator.validate(draft, catalog, semRestricoes);
    }

    private static GeneratedBuildDraft comResumo(String summary, String synergy, String direction) {
        return new GeneratedBuildDraft(
                summary, synergy, List.of(), "Ranger", "Deadeye", "Bow", "Ice Shot", List.of("Pierce"),
                new PassiveDirection(direction, FIVE_NOTABLES),
                new GearPriorities(List.of("x"), List.of("x"), List.of("x")),
                List.of("dica"));
    }

    @Nested
    @DisplayName("B1 — contrato hostil: o rascunho nunca derruba a aplicação")
    class ContratoHostil {

        @Test
        @DisplayName("ChoiceReason nulo vindo do LLM não lança exceção")
        void choiceReasonNulo() {
            List<ChoiceReason> reasons = Arrays.asList(new ChoiceReason("class", "porque sim"), null);
            assertThatCode(
                            () ->
                                    new GeneratedBuildDraft(
                                            "s", "s", reasons, "Ranger", "Deadeye", "Bow", "Ice Shot",
                                            List.of("Pierce"), null, null, null))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("rascunho inteiro nulo vira violações, não exceção")
        void tudoNulo() {
            var vazio = new GeneratedBuildDraft(null, null, null, null, null, null, null, null, null, null, null);
            assertThat(validar(vazio)).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("B2/B3 — duplicatas não inflam a build")
    class Duplicatas {

        @Test
        @DisplayName("B2: o mesmo notable 5 vezes não satisfaz o mínimo de 3 notables")
        void notableRepetido() {
            var draft = draft("Ranger", "Deadeye", "Bow", "Ice Shot", List.of("Pierce"), Collections.nCopies(5, "Swift Flight"));
            assertThat(validar(draft)).extracting(BuildViolation::type).contains(Type.NOTABLE_COUNT);
        }

        @Test
        @DisplayName("B2: notables repetidos com caixa diferente também contam uma vez só")
        void notableRepetidoComCaixa() {
            var draft = draft("Ranger", "Deadeye", "Bow", "Ice Shot", List.of("Pierce"),
                    List.of("Swift Flight", "swift flight", " SWIFT FLIGHT"));
            assertThat(validar(draft)).extracting(BuildViolation::type).contains(Type.NOTABLE_COUNT);
        }

        @Test
        @DisplayName("B3: o mesmo suporte duas vezes é reprovado")
        void suporteRepetido() {
            var draft = draft("Ranger", "Deadeye", "Bow", "Ice Shot", List.of("Pierce", "pierce"), FIVE_NOTABLES);
            assertThat(validar(draft)).extracting(BuildViolation::field).contains("supports");
        }
    }

    @Nested
    @DisplayName("B4 — quantidade de suportes")
    class QuantidadeDeSuportes {

        @Test
        @DisplayName("build sem nenhum suporte é reprovada")
        void semSuporte() {
            var draft = draft("Ranger", "Deadeye", "Bow", "Ice Shot", List.of(), FIVE_NOTABLES);
            assertThat(validar(draft)).extracting(BuildViolation::field).contains("supports");
        }

        @Test
        @DisplayName("mais de 5 suportes é reprovado")
        void suportesDemais() {
            var muitos = Catalog.builder(new CatalogVersion(9, "0.4", CatalogVersion.Status.PUBLISHED));
            CatalogFixtures.catalog().classes().forEach(muitos::characterClass);
            CatalogFixtures.catalog().ascendancies().forEach(muitos::ascendancy);
            CatalogFixtures.catalog().weaponTypes().forEach(muitos::weaponType);
            CatalogFixtures.catalog().skillGems().forEach(muitos::skillGem);
            CatalogFixtures.catalog().notables().forEach(muitos::notable);
            List<String> seis = List.of("S1", "S2", "S3", "S4", "S5", "S6");
            seis.forEach(s -> muitos.supportGem(new SupportGem(s)));
            var draft = draft("Ranger", "Deadeye", "Bow", "Ice Shot", seis, FIVE_NOTABLES);

            assertThat(validator.validate(draft, muitos.build(), semRestricoes))
                    .extracting(BuildViolation::field)
                    .contains("supports");
        }
    }

    @Nested
    @DisplayName("B5 — coerência de jogo entre skill e arma")
    class CoerenciaSkillArma {

        @Test
        @DisplayName("Ice Shot (skill de arco) com Staff é reprovada")
        void skillDeArcoComCajado() {
            var draft = draft("Witch", "Infernalist", "Staff", "Ice Shot", List.of("Pierce"), FIVE_NOTABLES);
            assertThat(validar(draft)).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("B6 — conteúdo obrigatório da tela de resultado")
    class ConteudoObrigatorio {

        @Test
        @DisplayName("rascunho sem resumo é reprovado")
        void semResumo() {
            assertThat(validar(comResumo(null, "sinergia", "direção")))
                    .extracting(BuildViolation::type, BuildViolation::field)
                    .contains(tuple(Type.MISSING_FIELD, "summary"));
        }

        @Test
        @DisplayName("rascunho sem sinergia é reprovado")
        void semSinergia() {
            assertThat(validar(comResumo("resumo", "  ", "direção")))
                    .extracting(BuildViolation::type, BuildViolation::field)
                    .contains(tuple(Type.MISSING_FIELD, "synergy"));
        }

        @Test
        @DisplayName("rascunho sem direção da árvore é reprovado")
        void semDirecao() {
            assertThat(validar(comResumo("resumo", "sinergia", "")))
                    .extracting(BuildViolation::type, BuildViolation::field)
                    .contains(tuple(Type.MISSING_FIELD, "passiveTree"));
        }
    }

    @Nested
    @DisplayName("B7 — variações de espaço vindas do LLM")
    class VariacoesDeEspaco {

        @ParameterizedTest(name = "\"{0}\" encontra Ice Shot")
        @ValueSource(strings = {"Ice  Shot", "Ice Shot", " Ice Shot ", "Ice\tShot"})
        void espacosVariados(String nome) {
            var draft = draft("Ranger", "Deadeye", "Bow", nome, List.of("Pierce"), FIVE_NOTABLES);
            assertThat(validar(draft)).isEmpty();
        }
    }

    @Nested
    @DisplayName("B8 — limites do intervalo de notables")
    class LimitesNotables {

        private List<String> n(int quantidade) {
            List<String> base = new ArrayList<>(FIVE_NOTABLES);
            base.add("Spare Notable");
            // catálogo da fixture tem 6 notables distintos; para 8 usamos um catálogo maior
            return base.subList(0, Math.min(quantidade, base.size()));
        }

        @Test
        @DisplayName("exatamente 3 notables distintos passa")
        void tres() {
            assertThat(validar(draft("Ranger", "Deadeye", "Bow", "Ice Shot", List.of("Pierce"), n(3)))).isEmpty();
        }

        @Test
        @DisplayName("exatamente 8 notables distintos passa e 9 falha")
        void oitoENove() {
            var b = Catalog.builder(new CatalogVersion(9, "0.4", CatalogVersion.Status.PUBLISHED));
            catalog.classes().forEach(b::characterClass);
            catalog.ascendancies().forEach(b::ascendancy);
            catalog.weaponTypes().forEach(b::weaponType);
            catalog.skillGems().forEach(b::skillGem);
            catalog.supportGems().forEach(b::supportGem);
            List<String> nove = List.of("N1", "N2", "N3", "N4", "N5", "N6", "N7", "N8", "N9");
            nove.forEach(x -> b.notable(new PassiveNotable(x, "")));
            Catalog grande = b.build();

            assertThat(validator.validate(draft("Ranger", "Deadeye", "Bow", "Ice Shot", List.of("Pierce"), nove.subList(0, 8)), grande, semRestricoes))
                    .isEmpty();
            assertThat(validator.validate(draft("Ranger", "Deadeye", "Bow", "Ice Shot", List.of("Pierce"), nove), grande, semRestricoes))
                    .extracting(BuildViolation::type)
                    .containsExactly(Type.NOTABLE_COUNT);
        }
    }

    @Nested
    @DisplayName("B9 — locale padrão hostil")
    class LocaleHostil {

        @Test
        @DisplayName("com locale turco, 'ICE SHOT' e 'IRON SKIN' continuam encontrados")
        void localeTurco() {
            Locale original = Locale.getDefault();
            try {
                Locale.setDefault(Locale.forLanguageTag("tr-TR"));
                var notables = new ArrayList<>(FIVE_NOTABLES);
                notables.set(4, "IRON SKIN");
                var draft = draft("RANGER", "DEADEYE", "BOW", "ICE SHOT", List.of("PIERCE"), notables);
                assertThat(validar(draft)).isEmpty();
            } finally {
                Locale.setDefault(original);
            }
        }
    }

    @Nested
    @DisplayName("B10 — imutabilidade depois da construção")
    class Imutabilidade {

        @Test
        @DisplayName("mexer no Builder depois de build() não altera o catálogo")
        void builderReutilizado() {
            var b = Catalog.builder(new CatalogVersion(9, "0.4", CatalogVersion.Status.PUBLISHED));
            Catalog c = b.build();
            b.skillGem(new SkillGem("Injected Gem"));
            assertThat(c.findSkillGem("Injected Gem")).isEmpty();
        }

        @Test
        @DisplayName("mexer na lista original depois de criar o rascunho não altera o rascunho")
        void listaOriginalMutada() {
            List<String> supports = new ArrayList<>(List.of("Pierce"));
            List<String> notables = new ArrayList<>(FIVE_NOTABLES);
            var draft = draft("Ranger", "Deadeye", "Bow", "Ice Shot", supports, notables);
            supports.add("Fake Support");
            notables.clear();
            assertThat(validar(draft)).isEmpty();
        }
    }

    @Nested
    @DisplayName("B11 — regra dos 100%: nome do tipo errado nunca passa")
    class TipoErrado {

        @Test
        @DisplayName("suporte usado como skill principal é reprovado")
        void suporteComoSkill() {
            assertThat(validar(draft("Ranger", "Deadeye", "Bow", "Pierce", List.of("Cold Infusion"), FIVE_NOTABLES)))
                    .extracting(BuildViolation::field).contains("mainSkill");
        }

        @Test
        @DisplayName("skill usada como suporte é reprovada")
        void skillComoSuporte() {
            assertThat(validar(draft("Ranger", "Deadeye", "Bow", "Ice Shot", List.of("Fireball"), FIVE_NOTABLES)))
                    .extracting(BuildViolation::field).contains("supports");
        }

        @Test
        @DisplayName("ascendência usada como classe é reprovada")
        void ascendenciaComoClasse() {
            assertThat(validar(draft("Deadeye", "Deadeye", "Bow", "Ice Shot", List.of("Pierce"), FIVE_NOTABLES)))
                    .extracting(BuildViolation::field).contains("class");
        }

        @Test
        @DisplayName("homóglifo cirílico ('Іce Shot') é reprovado")
        void homoglifo() {
            assertThat(validar(draft("Ranger", "Deadeye", "Bow", "Іce Shot", List.of("Pierce"), FIVE_NOTABLES)))
                    .extracting(BuildViolation::field).contains("mainSkill");
        }

        @Test
        @DisplayName("nome do catálogo com sufixo ('Ice Shot II') é reprovado")
        void sufixo() {
            assertThat(validar(draft("Ranger", "Deadeye", "Bow", "Ice Shot II", List.of("Pierce"), FIVE_NOTABLES)))
                    .extracting(BuildViolation::field).contains("mainSkill");
        }
    }
}
