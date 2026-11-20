package com.poe.poe2buildassistant;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
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

    /**
     * Lista branca, não negra: "Java puro" significa só JDK + o próprio domínio. Uma lista de pacotes
     * proibidos deixava passar Hibernate, o SDK do Gemini e qualquer biblioteca nova (chaos K2).
     */
    @ArchTest
    static final ArchRule domain_so_depende_de_java_e_dominio =
            classes()
                    .that()
                    .resideInAPackage("..domain..")
                    .should()
                    .onlyDependOnClassesThat()
                    .resideInAnyPackage("java..", "..domain..")
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

    /**
     * A aplicação só conhece domínio, as próprias portas e as duas anotações permitidas pelo ADR-002
     * § 1 ({@code @Service}, {@code @Transactional}). Sem isto, um caso de uso podia usar
     * {@code EntityManager} ou {@code ChatClient} direto (chaos K3).
     */
    @ArchTest
    static final ArchRule application_so_depende_de_dominio_e_portas =
            classes()
                    .that()
                    .resideInAPackage("..application..")
                    .should()
                    .onlyDependOnClassesThat()
                    .resideInAnyPackage(
                            "java..",
                            "..domain..",
                            "..application..",
                            "org.springframework.stereotype..",
                            "org.springframework.transaction.annotation..")
                    .allowEmptyShould(true)
                    .because("só @Service e @Transactional são permitidos na aplicação (ADR-002 § 1)");

    @ArchTest
    static final ArchRule adapters_nao_dependem_uns_dos_outros =
            slices().matching("..adapter.(*).(*)..")
                    .should()
                    .notDependOnEachOther()
                    .allowEmptyShould(true)
                    .because("um adaptador nunca chama outro; a comunicação passa pelas portas");
}
