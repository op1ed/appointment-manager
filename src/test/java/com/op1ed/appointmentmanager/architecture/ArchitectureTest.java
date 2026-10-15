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
                BASE + ".agent.application..", BASE + ".agent.domain..",
                BASE + ".shared.error.."
        ).should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework.http..", "org.springframework.web..", "jakarta.servlet..",
                "org.springframework.security.web..", "org.springframework.security.config.annotation.web..",
                "java.net.http..",
                BASE + "..web.."
        ).check(productionClasses);
    }

    @Test
    void appointmentsMustOnlyUseTheDoctorsApplicationEntryPoints() {
        noClasses().that().resideInAPackage(BASE + ".appointment..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE + ".doctor.domain..", BASE + ".doctor.infrastructure..",
                        BASE + ".doctor.web..", BASE + ".doctor.application.impl.."
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
                BASE + ".appointment.domain..", BASE + ".doctor.domain..", BASE + ".identity.domain..", BASE + ".agent.domain.."
        ).should().dependOnClassesThat().resideInAnyPackage(
                BASE + "..application..", BASE + "..infrastructure..", BASE + "..web.."
        ).check(productionClasses);
    }

    @Test
    void persistenceMustNotDependOnApplicationOrWebLayers() {
        noClasses().that().resideInAnyPackage(
                BASE + ".appointment.infrastructure..", BASE + ".doctor.infrastructure..",
                BASE + ".identity.infrastructure.persistence..", BASE + ".agent.infrastructure.persistence.."
        ).should().dependOnClassesThat().resideInAnyPackage(
                BASE + "..application..", BASE + "..web.."
        ).check(productionClasses);
    }

    @Test
    void webAdaptersMustNotAccessPersistenceDirectly() {
        noClasses().that().resideInAnyPackage(
                BASE + ".appointment.web..", BASE + ".doctor.web..", BASE + ".identity.web..", BASE + ".agent.web.."
        ).should().dependOnClassesThat().resideInAPackage(BASE + "..infrastructure..")
                .check(productionClasses);
    }

    @Test
    void sharedCodeMustNotOwnBusinessModuleDependencies() {
        noClasses().that().resideInAPackage(BASE + ".shared..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE + ".appointment..", BASE + ".doctor..", BASE + ".identity..", BASE + ".agent.."
                ).check(productionClasses);
    }

    @Test
    void businessModulesMustOnlyUseIdentityApplicationEntryPoints() {
        noClasses().that().resideInAnyPackage(BASE + ".appointment..", BASE + ".doctor..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE + ".identity.domain..", BASE + ".identity.infrastructure..", BASE + ".identity.web..",
                        BASE + ".identity.application.impl.."
                ).check(productionClasses);
    }

    @Test
    void identityMustNotDependOnAppointmentOrDoctorBusiness() {
        noClasses().that().resideInAPackage(BASE + ".identity..")
                .should().dependOnClassesThat().resideInAnyPackage(BASE + ".appointment..", BASE + ".doctor..")
                .check(productionClasses);
    }

    @Test
    void agentMustOnlyUseBusinessApplicationEntryPoints() {
        noClasses().that().resideInAPackage(BASE + ".agent..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE + ".appointment.domain..", BASE + ".appointment.infrastructure..", BASE + ".appointment.web..",
                        BASE + ".doctor.domain..", BASE + ".doctor.infrastructure..", BASE + ".doctor.web..",
                        BASE + ".identity.domain..", BASE + ".identity.infrastructure..", BASE + ".identity.web..",
                        BASE + ".appointment.application.impl..", BASE + ".doctor.application.impl..",
                        BASE + ".identity.application.impl.."
                ).check(productionClasses);
    }

    @Test
    void businessModulesMustNotDependOnAgent() {
        noClasses().that().resideInAnyPackage(BASE + ".appointment..", BASE + ".doctor..", BASE + ".identity..")
                .should().dependOnClassesThat().resideInAPackage(BASE + ".agent..")
                .check(productionClasses);
    }

    @Test
    void applicationAndDomainMustNotDependOnAgentFramework() {
        noClasses().that().resideInAnyPackage(BASE + "..application..", BASE + "..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("com.alibaba.cloud.ai..", "org.springframework.ai..")
                .check(productionClasses);
    }

    @Test
    void projectMustUseMybatisInsteadOfJpaPersistence() {
        noClasses().that().resideInAPackage(BASE + "..")
                .should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..", "org.hibernate..", "org.springframework.data.jpa..")
                .check(productionClasses);
    }

    @Test
    void callersMustDependOnServiceContractsInsteadOfImplementations() {
        noClasses().that().resideOutsideOfPackage(BASE + "..application.impl..")
                .should().dependOnClassesThat().resideInAPackage(BASE + "..application.impl..")
                .check(productionClasses);
    }
}
