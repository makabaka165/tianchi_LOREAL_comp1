package com.hmdp.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Enforces the customer-service context map (execution plan §2.1):
 *
 * <pre>
 * servicedata  &lt;- serviceassist -&gt; ai
 *                     |
 *                     v
 *                  riskops
 * </pre>
 *
 * servicedata and riskops depend on no other context; serviceassist consumes their
 * public application contracts/ports only, and reaches the agent platform without
 * touching the AI domain security model. The AI platform never depends on any
 * customer-service context. Several subject sets are still empty (packages arrive
 * with DATA-001/RISK-001), hence allowEmptyShould(true); the rules bite as soon as
 * the first class appears.
 */
@AnalyzeClasses(packages = "com.hmdp", importOptions = ImportOption.DoNotIncludeTests.class)
class CustomerServiceModuleBoundaryTest {

    @ArchTest
    static final ArchRule ai_platform_must_not_depend_on_customer_service_contexts = noClasses()
            .that().resideInAPackage("com.hmdp.ai..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.hmdp.servicedata..", "com.hmdp.serviceassist..", "com.hmdp.riskops..")
            .because("the agent platform is generic; customer-service adapters plug into it, never the reverse");

    @ArchTest
    static final ArchRule servicedata_depends_on_no_other_context = noClasses()
            .that().resideInAPackage("com.hmdp.servicedata..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.hmdp.serviceassist..", "com.hmdp.riskops..", "com.hmdp.ai..")
            .allowEmptyShould(true)
            .because("servicedata owns source facts only and must stay consumable by every other context");

    @ArchTest
    static final ArchRule riskops_depends_on_no_other_context = noClasses()
            .that().resideInAPackage("com.hmdp.riskops..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.hmdp.servicedata..", "com.hmdp.serviceassist..", "com.hmdp.ai..")
            .allowEmptyShould(true)
            .because("riskops consumes typed observations handed to it; it never reads other contexts directly");

    @ArchTest
    static final ArchRule riskops_domain_is_pure = noClasses()
            .that().resideInAPackage("com.hmdp.riskops.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "javax.sql..", "java.sql..",
                    "org.apache.ibatis..", "com.baomidou..", "org.springframework.jdbc..")
            .allowEmptyShould(true)
            .because("the risk state machine must stay a plain domain model, testable without infrastructure");

    @ArchTest
    static final ArchRule serviceassist_uses_only_public_surfaces_of_servicedata = noClasses()
            .that().resideInAPackage("com.hmdp.serviceassist..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.hmdp.servicedata.domain..", "com.hmdp.servicedata.infrastructure..",
                    "com.hmdp.servicedata.api..")
            .allowEmptyShould(true)
            .because("cross-context reads go through servicedata.application contract/port types only");

    @ArchTest
    static final ArchRule serviceassist_uses_only_public_surfaces_of_riskops = noClasses()
            .that().resideInAPackage("com.hmdp.serviceassist..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.hmdp.riskops.domain..", "com.hmdp.riskops.infrastructure..",
                    "com.hmdp.riskops.api..")
            .allowEmptyShould(true)
            .because("serviceassist may only consume riskops public read DTOs and submit ports");

    @ArchTest
    static final ArchRule customer_service_contexts_never_use_ai_domain_security = noClasses()
            .that().resideInAnyPackage(
                    "com.hmdp.servicedata..", "com.hmdp.serviceassist..",
                    "com.hmdp.riskops..", "com.hmdp.security.customer..")
            .should().dependOnClassesThat().resideInAPackage("com.hmdp.ai.domain.security..")
            .allowEmptyShould(true)
            .because("customer-service scope security is technical shared infrastructure, decoupled from AiPermission");

    @ArchTest
    static final ArchRule customer_service_contexts_never_touch_legacy_persistence = noClasses()
            .that().resideInAnyPackage(
                    "com.hmdp.servicedata..", "com.hmdp.serviceassist..", "com.hmdp.riskops..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.hmdp.mapper..", "com.hmdp.entity..")
            .allowEmptyShould(true)
            .because("cs_data_*/cs_risk_*/cs_assist_* tables are owned by their contexts; legacy mappers stay out");

    @ArchTest
    static final ArchRule api_layers_do_not_reach_into_infrastructure = noClasses()
            .that().resideInAnyPackage(
                    "com.hmdp.servicedata.api..", "com.hmdp.serviceassist.api..", "com.hmdp.riskops.api..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.hmdp.servicedata.infrastructure..", "com.hmdp.serviceassist.infrastructure..",
                    "com.hmdp.riskops.infrastructure..")
            .allowEmptyShould(true)
            .because("controllers speak to application services; JDBC adapters stay behind ports");

    @ArchTest
    static final ArchRule shared_customer_security_package_stays_neutral = noClasses()
            .that().resideInAPackage("com.hmdp.security.customer..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.hmdp.servicedata..", "com.hmdp.serviceassist..",
                    "com.hmdp.riskops..", "com.hmdp.ai..")
            .because("the scope entry point must not pull any business context or the AI platform");

    @ArchTest
    static final ArchRule servicedata_slices_are_cycle_free = slices()
            .matching("com.hmdp.servicedata.(*)..").should().beFreeOfCycles()
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule serviceassist_slices_are_cycle_free = slices()
            .matching("com.hmdp.serviceassist.(*)..").should().beFreeOfCycles()
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule riskops_slices_are_cycle_free = slices()
            .matching("com.hmdp.riskops.(*)..").should().beFreeOfCycles()
            .allowEmptyShould(true);
}
