package com.op1ed.appointmentmanager.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureTest {

    private static final String BASE = "com.op1ed.appointmentmanager";
    private static JavaClasses productionClasses;

    @BeforeAll
    static void importProductionClasses() {
        productionClasses = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages(BASE);
    }

    @Test
    void businessCodeMustNotDependOnHttpOrWebAdapters() {
        noClasses().that().resideInAnyPackage(
                BASE + ".appointment.application..", BASE + ".appointment.domain..",
                BASE + ".doctor.application..", BASE + ".doctor.domain..",
                BASE + ".identity.application..", BASE + ".identity.domain..",
                BASE + ".shared.error.."
        ).should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework.http..", "org.springframework.web..", "jakarta.servlet..",
                "org.springframework.security.web..", "org.springframework.security.config.annotation.web..",
                BASE + "..web.."
        ).check(productionClasses);
    }

    @Test
    void appointmentsMustOnlyUseTheDoctorsApplicationEntryPoints() {
        noClasses().that().resideInAPackage(BASE + ".appointment..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE + ".doctor.domain..", BASE + ".doctor.infrastructure..",
                        BASE + ".doctor.web.."
                ).check(productionClasses);
    }

    @Test
    void doctorsMustNotDependOnAppointments() {
        noClasses().that().resideInAPackage(BASE + ".doctor..")
                .should().dependOnClassesThat().resideInAPackage(BASE + ".appointment..")
                .check(productionClasses);
    }

    @Test
    void domainModelsMustNotDependOnOuterLayers() {
        noClasses().that().resideInAnyPackage(
                BASE + ".appointment.domain..", BASE + ".doctor.domain..", BASE + ".identity.domain.."
        ).should().dependOnClassesThat().resideInAnyPackage(
                BASE + "..application..", BASE + "..infrastructure..", BASE + "..web.."
        ).check(productionClasses);
    }

    @Test
    void persistenceMustNotDependOnApplicationOrWebLayers() {
        noClasses().that().resideInAnyPackage(
                BASE + ".appointment.infrastructure..", BASE + ".doctor.infrastructure..",
                BASE + ".identity.infrastructure.persistence.."
        ).should().dependOnClassesThat().resideInAnyPackage(
                BASE + "..application..", BASE + "..web.."
        ).check(productionClasses);
    }

    @Test
    void webAdaptersMustNotAccessPersistenceDirectly() {
        noClasses().that().resideInAnyPackage(
                BASE + ".appointment.web..", BASE + ".doctor.web..", BASE + ".identity.web.."
        ).should().dependOnClassesThat().resideInAPackage(BASE + "..infrastructure..")
                .check(productionClasses);
    }

    @Test
    void sharedCodeMustNotOwnBusinessModuleDependencies() {
        noClasses().that().resideInAPackage(BASE + ".shared..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE + ".appointment..", BASE + ".doctor..", BASE + ".identity.."
                ).check(productionClasses);
    }

    @Test
    void businessModulesMustOnlyUseIdentityApplicationEntryPoints() {
        noClasses().that().resideInAnyPackage(BASE + ".appointment..", BASE + ".doctor..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE + ".identity.domain..", BASE + ".identity.infrastructure..", BASE + ".identity.web.."
                ).check(productionClasses);
    }

    @Test
    void identityMustNotDependOnAppointmentOrDoctorBusiness() {
        noClasses().that().resideInAPackage(BASE + ".identity..")
                .should().dependOnClassesThat().resideInAnyPackage(BASE + ".appointment..", BASE + ".doctor..")
                .check(productionClasses);
    }
}
