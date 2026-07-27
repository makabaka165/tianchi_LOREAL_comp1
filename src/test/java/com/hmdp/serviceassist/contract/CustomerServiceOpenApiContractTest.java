package com.hmdp.serviceassist.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.common.ErrorCode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Freezes the customer-service API surface (CONTRACT-001): the 7.3 route set, enum
 * values shared between the OpenAPI document and the business payload schema, the
 * CS_* error code section, and (for future tasks) every controller in the three
 * customer-service contexts must be documented in openapi.yaml.
 */
class CustomerServiceOpenApiContractTest {
    private static Map<String, Object> openapi;
    private static Map<String, Object> paths;
    private static Map<String, Object> schemas;
    private static JsonNode businessSchema;

    private static final List<String> FROZEN_ROUTES = Arrays.asList(
            "/api/v1/customer-service/imports/preview",
            "/api/v1/customer-service/imports/{batchId}",
            "/api/v1/customer-service/imports/{batchId}/errors",
            "/api/v1/customer-service/imports/{batchId}/confirm",
            "/api/v1/customer-service/conversations",
            "/api/v1/customer-service/conversations/{conversationId}/workspace",
            "/api/v1/customer-service/conversations/{conversationId}/assistance-requests",
            "/api/v1/customer-service/assistance-requests/{requestId}",
            "/api/v1/customer-service/suggestions/{suggestionId}/decisions",
            "/api/v1/customer-service/risk-alerts",
            "/api/v1/customer-service/risk-alerts/{alertId}",
            "/api/v1/customer-service/risk-alerts/{alertId}/acknowledge",
            "/api/v1/customer-service/risk-alerts/{alertId}/assign",
            "/api/v1/customer-service/risk-alerts/{alertId}/start",
            "/api/v1/customer-service/risk-alerts/{alertId}/resolve",
            "/api/v1/customer-service/risk-alerts/{alertId}/dismiss");

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void load() throws IOException {
        try (InputStream in = Files.newInputStream(Path.of("docs/api/openapi.yaml"))) {
            openapi = new Yaml().load(in);
        }
        paths = (Map<String, Object>) openapi.get("paths");
        Map<String, Object> components = (Map<String, Object>) openapi.get("components");
        schemas = (Map<String, Object>) components.get("schemas");
        businessSchema = new ObjectMapper().readTree(Files.readString(
                Path.of("docs/contracts/customer-service-assistance-output.schema.json"),
                StandardCharsets.UTF_8));
    }

    @Test
    void allFrozenCustomerServiceRoutesAreDocumented() {
        assertThat(paths.keySet()).containsAll(FROZEN_ROUTES);
    }

    @Test
    @SuppressWarnings("unchecked")
    void riskEnumsMatchTheBusinessPayloadSchema() {
        List<String> openapiRiskTypes =
                (List<String>) ((Map<String, Object>) schemas.get("CsRiskType")).get("enum");
        List<String> schemaRiskTypes = new ArrayList<>();
        businessSchema.at("/definitions/riskSignal/properties/type/enum")
                .forEach(node -> schemaRiskTypes.add(node.asText()));
        assertThat(openapiRiskTypes).containsExactlyElementsOf(schemaRiskTypes);

        List<String> openapiSeverities =
                (List<String>) ((Map<String, Object>) schemas.get("CsRiskSeverity")).get("enum");
        List<String> schemaSeverities = new ArrayList<>();
        businessSchema.at("/definitions/riskSignal/properties/severity/enum")
                .forEach(node -> schemaSeverities.add(node.asText()));
        assertThat(openapiSeverities).containsExactlyElementsOf(schemaSeverities);
    }

    @Test
    @SuppressWarnings("unchecked")
    void frozenEnumsHaveTheAgreedValues() {
        assertThat((List<String>) ((Map<String, Object>) schemas.get("CsRiskAlertStatus")).get("enum"))
                .containsExactly("OPEN", "ACKNOWLEDGED", "IN_PROGRESS", "RESOLVED", "DISMISSED");
        assertThat((List<String>) ((Map<String, Object>) schemas.get("CsDecisionType")).get("enum"))
                .containsExactly("ACCEPT", "ACCEPT_WITH_EDIT", "REJECT");
        assertThat((List<String>) ((Map<String, Object>) schemas.get("CsGenerationMode")).get("enum"))
                .containsExactly("LIVE", "DETERMINISTIC_FALLBACK", "DEMO_FIXTURE");
        assertThat((List<String>) ((Map<String, Object>) schemas.get("CsAssistanceRequestStatus")).get("enum"))
                .containsExactly("CREATED", "RUN_QUEUED", "RUNNING", "COMPLETED", "FAILED",
                        "FALLBACK_COMPLETED");
        assertThat((List<String>) ((Map<String, Object>) schemas.get("CsSuggestionStatus")).get("enum"))
                .containsExactly("ACTIVE", "STALE", "EXPIRED");
    }

    @Test
    void customerServiceErrorCodesAreDefined() {
        Set<String> names = Arrays.stream(ErrorCode.values())
                .map(Enum::name)
                .collect(Collectors.toSet());
        assertThat(names).contains(
                "CS_FEATURE_DISABLED",
                "CS_RESOURCE_NOT_FOUND",
                "CS_IMPORT_VALIDATION_FAILED",
                "CS_IMPORT_CONFLICT",
                "CS_ASSISTANCE_CONFLICT",
                "CS_OUTPUT_INVALID",
                "CS_SUGGESTION_STALE",
                "CS_SUGGESTION_DECIDED",
                "CS_RISK_VERSION_CONFLICT",
                "CS_RISK_INVALID_TRANSITION");
    }

    @Test
    void customerServiceErrorCodesFollowHttpFamilyNumbering() {
        assertThat(ErrorCode.CS_RESOURCE_NOT_FOUND.getCode()).isBetween(40400, 40499);
        assertThat(ErrorCode.CS_IMPORT_CONFLICT.getCode()).isBetween(40900, 40999);
        assertThat(ErrorCode.CS_ASSISTANCE_CONFLICT.getCode()).isBetween(40900, 40999);
        assertThat(ErrorCode.CS_SUGGESTION_STALE.getCode()).isBetween(40900, 40999);
        assertThat(ErrorCode.CS_SUGGESTION_DECIDED.getCode()).isBetween(40900, 40999);
        assertThat(ErrorCode.CS_RISK_VERSION_CONFLICT.getCode()).isBetween(40900, 40999);
        assertThat(ErrorCode.CS_RISK_INVALID_TRANSITION.getCode()).isBetween(40900, 40999);
        assertThat(ErrorCode.CS_IMPORT_VALIDATION_FAILED.getCode()).isBetween(42200, 42299);
        assertThat(ErrorCode.CS_FEATURE_DISABLED.getCode()).isBetween(50300, 50399);
    }

    @Test
    @SuppressWarnings("unchecked")
    void assistanceCreationReturns202WithNullableRunId() {
        Map<String, Object> path = (Map<String, Object>) paths.get(
                "/api/v1/customer-service/conversations/{conversationId}/assistance-requests");
        Map<String, Object> post = (Map<String, Object>) path.get("post");
        Map<String, Object> responses = (Map<String, Object>) post.get("responses");
        assertThat(responses).containsKey("202");

        Map<String, Object> created = (Map<String, Object>) schemas.get("CsAssistanceRequestCreated");
        Map<String, Object> properties = (Map<String, Object>) created.get("properties");
        Map<String, Object> agentRunId = (Map<String, Object>) properties.get("agentRunId");
        assertThat(agentRunId.get("nullable")).isEqualTo(Boolean.TRUE);
        assertThat((List<String>) created.get("required")).doesNotContain("agentRunId");
    }

    @Test
    @SuppressWarnings("unchecked")
    void riskCommandsAllCarryExpectedVersion() {
        for (String schemaName : Arrays.asList(
                "CsRiskCommandRequest", "CsRiskAssignRequest", "CsRiskReasonedCommandRequest")) {
            Map<String, Object> schema = (Map<String, Object>) schemas.get(schemaName);
            assertThat((List<String>) schema.get("required"))
                    .as("%s.required", schemaName)
                    .contains("expectedVersion");
            assertThat(schema.get("additionalProperties")).isEqualTo(Boolean.FALSE);
        }
        assertThat((List<String>) ((Map<String, Object>) schemas.get("CsRiskAssignRequest"))
                .get("required")).contains("assigneeId");
        assertThat((List<String>) ((Map<String, Object>) schemas.get("CsRiskReasonedCommandRequest"))
                .get("required")).contains("reason");
    }

    @Test
    @SuppressWarnings("unchecked")
    void importPreviewAndConfirmationExposeAllStagingPreconditions() {
        Map<String, Object> batch = (Map<String, Object>) schemas.get("CsImportBatch");
        assertThat((List<String>) batch.get("required")).contains(
                "batchId", "sourceSha256", "parserVersion", "counts", "warningCount",
                "errorCount", "blockingErrorCount", "confirmable", "status", "expiresAt",
                "version");

        Map<String, Object> confirm =
                (Map<String, Object>) schemas.get("CsImportConfirmRequest");
        assertThat((List<String>) confirm.get("required")).containsExactlyInAnyOrder(
                "expectedSourceSha256", "expectedParserVersion", "expectedVersion");
        assertThat(confirm.get("additionalProperties")).isEqualTo(Boolean.FALSE);

        Map<String, Object> error = (Map<String, Object>) schemas.get("CsImportError");
        assertThat((List<String>) error.get("required"))
                .contains("sheet", "row", "errorCode", "severity", "message");

        Map<String, Object> confirmPath = (Map<String, Object>) paths.get(
                "/api/v1/customer-service/imports/{batchId}/confirm");
        Map<String, Object> confirmPost = (Map<String, Object>) confirmPath.get("post");
        assertThat(confirmPost.get("summary").toString()).contains("atomically commit");
        assertThat(confirmPost.get("description").toString())
                .contains("CONFIRMED").doesNotContain("DATA-003 only");
    }

    @Test
    @SuppressWarnings("unchecked")
    void importCountsAreTypedRatherThanAnArbitraryMap() {
        Map<String, Object> counts = (Map<String, Object>) schemas.get("CsImportCounts");
        assertThat(counts.get("additionalProperties")).isEqualTo(Boolean.FALSE);
        Map<String, Object> properties = (Map<String, Object>) counts.get("properties");
        assertThat(properties.keySet()).containsExactlyInAnyOrder(
                "consumerAliases", "conversations", "messages", "orderSnapshots",
                "serviceCases", "sourceLinks", "missingMedia");
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyCustomerServiceRouteRequiresScopeHeaders() {
        for (String route : FROZEN_ROUTES) {
            Map<String, Object> pathItem = (Map<String, Object>) paths.get(route);
            Set<String> referencedParameters = new LinkedHashSet<>();
            collectParameterRefs(pathItem.get("parameters"), referencedParameters);
            for (Map.Entry<String, Object> entry : pathItem.entrySet()) {
                if (entry.getValue() instanceof Map) {
                    collectParameterRefs(((Map<String, Object>) entry.getValue()).get("parameters"),
                            referencedParameters);
                }
            }
            assertThat(referencedParameters)
                    .as("route %s must require tenant/workspace headers", route)
                    .contains("#/components/parameters/TenantHeader",
                            "#/components/parameters/WorkspaceHeader");
        }
    }

    @SuppressWarnings("unchecked")
    private void collectParameterRefs(Object parameters, Set<String> into) {
        if (!(parameters instanceof List)) {
            return;
        }
        for (Object parameter : (List<Object>) parameters) {
            if (parameter instanceof Map) {
                Object ref = ((Map<String, Object>) parameter).get("$ref");
                if (ref != null) {
                    into.add(ref.toString());
                }
            }
        }
    }

    /**
     * Future customer-service controllers must be documented: every @RequestMapping
     * path found in the three context api packages has to exist in openapi.yaml.
     * Today the packages are empty, so this passes vacuously and starts guarding as
     * soon as DATA-003+ add real controllers.
     */
    @Test
    void everyCustomerServiceControllerPathIsDocumented() throws Exception {
        List<String> controllerPaths = new ArrayList<>();
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        for (String basePackage : Arrays.asList(
                "com.hmdp.servicedata.api", "com.hmdp.serviceassist.api", "com.hmdp.riskops.api")) {
            for (var candidate : scanner.findCandidateComponents(basePackage)) {
                Class<?> controller = Class.forName(candidate.getBeanClassName());
                RequestMapping classMapping = AnnotatedElementUtils.findMergedAnnotation(
                        controller, RequestMapping.class);
                String prefix = classMapping == null || classMapping.value().length == 0
                        ? "" : classMapping.value()[0];
                for (Method method : controller.getDeclaredMethods()) {
                    RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(
                            method, RequestMapping.class);
                    if (mapping == null) {
                        continue;
                    }
                    String suffix = mapping.value().length == 0 && mapping.path().length == 0
                            ? "" : (mapping.path().length > 0 ? mapping.path()[0] : mapping.value()[0]);
                    String full = (prefix + suffix).replaceAll("//+", "/");
                    if (full.endsWith("/") && full.length() > 1) {
                        full = full.substring(0, full.length() - 1);
                    }
                    controllerPaths.add(full);
                }
            }
        }
        assertThat(paths.keySet()).containsAll(controllerPaths);
    }
}
