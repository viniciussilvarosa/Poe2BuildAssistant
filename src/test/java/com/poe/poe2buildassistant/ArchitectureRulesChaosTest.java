package com.poe.poe2buildassistant;

import static org.assertj.core.api.Assertions.assertThat;

import com.poe.poe2buildassistant.chaos.archfixture.adapter.in.web.WebUsesPersistence;
import com.poe.poe2buildassistant.chaos.archfixture.adapter.out.ai.AiUsesPersistence;
import com.poe.poe2buildassistant.chaos.archfixture.adapter.out.ai.FakeAiAdapter;
import com.poe.poe2buildassistant.chaos.archfixture.adapter.out.persistence.FakeRepo;
import com.poe.poe2buildassistant.chaos.archfixture.application.JpaInApplication;
import com.poe.poe2buildassistant.chaos.archfixture.application.SpringAiInApplication;
import com.poe.poe2buildassistant.chaos.archfixture.application.UsesAdapter;
import com.poe.poe2buildassistant.chaos.archfixture.domain.GenAiInDomain;
import com.poe.poe2buildassistant.chaos.archfixture.domain.HibernateInDomain;
import com.poe.poe2buildassistant.chaos.archfixture.domain.Slf4jInDomain;
import com.poe.poe2buildassistant.chaos.archfixture.domain.SpringInDomain;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Ataques às próprias regras de arquitetura: uma regra que nunca reprova nada é teatro. As fixtures
 * em {@code chaos.archfixture} violam a regra de propósito; cada teste exige que a regra as pegue.
 */
class ArchitectureRulesChaosTest {

    private static JavaClasses importar(Class<?>... classes) {
        return new ClassFileImporter().importClasses(classes);
    }

    /** O domínio só pode passar se TODAS as regras de domínio aceitarem. */
    private static boolean dominioReprovado(JavaClasses classes) {
        return List.of(
                        HexagonalArchitectureTest.domain_nao_depende_de_frameworks,
                        HexagonalArchitectureTest.domain_nao_depende_de_camadas_externas)
                .stream()
                .anyMatch(r -> r.evaluate(classes).hasViolation());
    }

    private static boolean aplicacaoReprovada(JavaClasses classes) {
        return HexagonalArchitectureTest.application_nao_depende_de_adapter.evaluate(classes).hasViolation();
    }

    @Nested
    @DisplayName("A10 — as regras existentes detectam violações reais")
    class RegrasNaoSaoVazias {

        @Test
        @DisplayName("domínio usando Spring é reprovado")
        void dominioComSpring() {
            assertThat(dominioReprovado(importar(SpringInDomain.class))).isTrue();
        }

        @Test
        @DisplayName("aplicação dependendo de adapter é reprovada")
        void aplicacaoComAdapter() {
            assertThat(aplicacaoReprovada(importar(UsesAdapter.class, FakeAiAdapter.class))).isTrue();
        }

        @Test
        @DisplayName("adapter de entrada usando adapter de saída é reprovado")
        void webUsandoPersistencia() {
            ArchRule rule = HexagonalArchitectureTest.adapters_nao_dependem_uns_dos_outros;
            assertThat(rule.evaluate(importar(WebUsesPersistence.class, FakeRepo.class)).hasViolation())
                    .isTrue();
        }

        @Test
        @DisplayName("adapter de saída usando outro adapter de saída é reprovado")
        void iaUsandoPersistencia() {
            ArchRule rule = HexagonalArchitectureTest.adapters_nao_dependem_uns_dos_outros;
            assertThat(rule.evaluate(importar(AiUsesPersistence.class, FakeRepo.class)).hasViolation())
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("A11 — domínio é Java puro (ADR-002 § 1)")
    class DominioJavaPuro {

        @Test
        @DisplayName("domínio com anotação do Hibernate é reprovado")
        void dominioComHibernate() {
            assertThat(dominioReprovado(importar(HibernateInDomain.class))).isTrue();
        }

        @Test
        @DisplayName("domínio acoplado ao SDK do Gemini é reprovado")
        void dominioComGenAi() {
            assertThat(dominioReprovado(importar(GenAiInDomain.class))).isTrue();
        }

        @Test
        @DisplayName("domínio com logger SLF4J é reprovado")
        void dominioComSlf4j() {
            assertThat(dominioReprovado(importar(Slf4jInDomain.class))).isTrue();
        }
    }

    @Nested
    @DisplayName("A12 — aplicação só conhece domínio e portas (ADR-002 § 1)")
    class AplicacaoSemTecnologia {

        @Test
        @DisplayName("caso de uso com EntityManager (JPA) é reprovado")
        void aplicacaoComJpa() {
            assertThat(aplicacaoReprovada(importar(JpaInApplication.class))).isTrue();
        }

        @Test
        @DisplayName("caso de uso com ChatClient (Spring AI) é reprovado")
        void aplicacaoComSpringAi() {
            assertThat(aplicacaoReprovada(importar(SpringAiInApplication.class))).isTrue();
        }
    }
}
