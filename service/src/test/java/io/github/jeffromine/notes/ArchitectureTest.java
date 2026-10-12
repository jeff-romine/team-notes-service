package io.github.jeffromine.notes;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.Random;
import java.util.UUID;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * The structural rules that keep the core pure and the shell honest: no clock, no randomness, no I/O libraries
 * in the core, and no adapter reaching into another.
 */
class ArchitectureTest {

    private static final String ROOT = "io.github.jeffromine.notes";
    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(ROOT);
    }

    @Test
    void coreAndModelDependOnlyOnJavaAndEachOther() {
        classes().that().resideInAnyPackage(ROOT + ".domain..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage("java..", ROOT + ".domain..", "org.commonmark..")
                .check(classes);
    }

    @Test
    void domainNeverReadsTheClockOrRandomness() {
        noClasses().that().resideInAnyPackage(ROOT + ".domain..")
                .should().callMethod(Instant.class, "now")
                .orShould().callMethod(System.class, "currentTimeMillis")
                .orShould().callMethod(System.class, "nanoTime")
                .orShould().callMethod(UUID.class, "randomUUID")
                .orShould().dependOnClassesThat().belongToAnyOf(Clock.class, Random.class)
                .check(classes);
    }

    @Test
    void shellDependsOnlyOnDomainAndItsOwnPorts() {
        classes().that().resideInAPackage(ROOT + ".application..")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage("java..", ROOT + ".domain..", ROOT + ".application..", "org.slf4j..")
                .check(classes);
    }

    @Test
    void adaptersDoNotDependOnEachOtherOrOnTheWebLayer() {
        noClasses().that().resideInAPackage(ROOT + ".adapter.persistence..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        ROOT + ".adapter.search..", ROOT + ".adapter.web..", ROOT + ".adapter.events..")
                .check(classes);
        noClasses().that().resideInAPackage(ROOT + ".adapter.search..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        ROOT + ".adapter.persistence..", ROOT + ".adapter.web..", ROOT + ".adapter.events..")
                .check(classes);
        noClasses().that().resideInAPackage(ROOT + ".adapter.web..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        ROOT + ".adapter.persistence..", ROOT + ".adapter.search..", ROOT + ".adapter.blob..")
                .check(classes);
    }
}
