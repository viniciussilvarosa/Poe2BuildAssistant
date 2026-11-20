package com.poe.poe2buildassistant;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Regras de dependência da arquitetura hexagonal (ADR-002 § 1 e § 6).
 *
 * <p>{@code allowEmptyShould(true)}: as camadas ainda podem estar vazias nos primeiros cards; a
 * regra passa a valer assim que a primeira classe entra no pacote.
 */
@AnalyzeClasses(
        packages = "com.poe.poe2buildassistant",
        importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    @ArchTest
    static final ArchRule domain_nao_depende_de_frameworks =
            noClasses()
                    .that()
                    .resideInAPackage("..domain..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.springframework..",
                            "jakarta.persistence..",
                            "lombok..",
                            "com.fasterxml..",
                            "tools.jackson..")
                    .allowEmptyShould(true)
                    .because("o domínio é Java puro (ADR-002 § 1)");

    @ArchTest
    static final ArchRule domain_nao_depende_de_camadas_externas =
            noClasses()
                    .that()
                    .resideInAPackage("..domain..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("..application..", "..adapter..", "..config..")
                    .allowEmptyShould(true)
                    .because("as dependências apontam para dentro, em direção ao domínio");

    @ArchTest
    static final ArchRule application_nao_depende_de_adapter =
            noClasses()
                    .that()
                    .resideInAPackage("..application..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("..adapter..", "..config..")
                    .allowEmptyShould(true)
                    .because("casos de uso só conhecem o domínio e as próprias portas");

    @ArchTest
    static final ArchRule adapters_nao_dependem_uns_dos_outros =
            slices().matching("..adapter.(*).(*)..")
                    .should()
                    .notDependOnEachOther()
                    .allowEmptyShould(true)
                    .because("um adaptador nunca chama outro; a comunicação passa pelas portas");
}
