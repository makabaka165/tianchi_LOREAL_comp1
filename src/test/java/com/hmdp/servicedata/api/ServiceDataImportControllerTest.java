package com.hmdp.servicedata.api;

import com.hmdp.common.ErrorCode;
import com.hmdp.config.WebExceptionAdvice;
import com.hmdp.security.customer.CustomerServiceFeatureGateFilter;
import com.hmdp.security.customer.CustomerServicePermission;
import com.hmdp.security.customer.CustomerServiceScopeContext;
import com.hmdp.security.customer.CustomerServiceScopeContextHolder;
import com.hmdp.security.customer.RequireCustomerServicePermission;
import com.hmdp.servicedata.api.dto.ConfirmServiceDataImportRequest;
import com.hmdp.servicedata.application.contract.ConfirmServiceDataImportCommand;
import com.hmdp.servicedata.application.contract.ServiceDataImportBatchView;
import com.hmdp.servicedata.application.contract.ServiceDataImportConfirmationView;
import com.hmdp.servicedata.application.contract.ServiceDataImportCounts;
import com.hmdp.servicedata.application.contract.ServiceDataImportErrorPage;
import com.hmdp.servicedata.application.contract.ServiceDataImportErrorView;
import com.hmdp.servicedata.application.contract.ServiceDataImportScope;
import com.hmdp.servicedata.application.contract.ServiceDataImportUpload;
import com.hmdp.servicedata.application.port.in.ConfirmServiceDataImportUseCase;
import com.hmdp.servicedata.application.port.in.GetServiceDataImportBatchUseCase;
import com.hmdp.servicedata.application.port.in.GetServiceDataImportErrorsUseCase;
import com.hmdp.servicedata.application.port.in.PreviewServiceDataImportUseCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ServiceDataImportControllerTest {
    private static final String SHA = "a".repeat(64);
    private static final Instant EXPIRES = Instant.parse("2026-07-28T03:00:00Z");

    @Mock
    private PreviewServiceDataImportUseCase preview;
    @Mock
    private GetServiceDataImportBatchUseCase getBatch;
    @Mock
    private GetServiceDataImportErrorsUseCase getErrors;
    @Mock
    private ConfirmServiceDataImportUseCase confirm;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        CustomerServiceScopeContextHolder.set(new CustomerServiceScopeContext(
                "operator-7", "tenant-a", "workspace-a",
                EnumSet.of(CustomerServicePermission.DATA_IMPORT)));
        ServiceDataImportController controller =
                new ServiceDataImportController(preview, getBatch, getErrors, confirm);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ServiceDataImportExceptionHandler(),
                        new WebExceptionAdvice())
                .build();
    }

    @AfterEach
    void tearDown() {
        CustomerServiceScopeContextHolder.clear();
    }

    @Test
    void previewAcceptsMultipartFileAndReturnsTheTypedBatchContract() throws Exception {
        when(preview.preview(any(), any())).thenReturn(batch());
        byte[] body = "sanitized-ooxml".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "data.xlsx", xlsxMime(), body);

        mvc.perform(multipart("/api/v1/customer-service/imports/preview").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchId").value("batch-1"))
                .andExpect(jsonPath("$.sourceSha256").value(SHA))
                .andExpect(jsonPath("$.parserVersion").value("competition-workbook-v1"))
                .andExpect(jsonPath("$.counts.messages").value(3))
                .andExpect(jsonPath("$.warningCount").value(1))
                .andExpect(jsonPath("$.errorCount").value(0))
                .andExpect(jsonPath("$.status").value("READY_TO_CONFIRM"))
                .andExpect(jsonPath("$.expiresAt").value(EXPIRES.toString()))
                .andExpect(jsonPath("$.version").value(4));

        ArgumentCaptor<ServiceDataImportScope> scope =
                ArgumentCaptor.forClass(ServiceDataImportScope.class);
        ArgumentCaptor<ServiceDataImportUpload> upload =
                ArgumentCaptor.forClass(ServiceDataImportUpload.class);
        verify(preview).preview(scope.capture(), upload.capture());
        assertThat(scope.getValue().getWorkspaceId()).isEqualTo("workspace-a");
        assertThat(scope.getValue().getActorId()).isEqualTo("operator-7");
        assertThat(upload.getValue().getFileName()).isEqualTo("data.xlsx");
        assertThat(upload.getValue().getContentType()).isEqualTo(xlsxMime());
        assertThat(upload.getValue().openStream().readAllBytes()).isEqualTo(body);
    }

    @Test
    void getAndPagedErrorsUseTheCurrentScopeAndNeverExposeRawValues() throws Exception {
        when(getBatch.getBatch(any(), eq("batch-1"))).thenReturn(batch());
        when(getErrors.getErrors(any(), eq("batch-1"), eq(2), eq(20)))
                .thenReturn(new ServiceDataImportErrorPage(List.of(
                        new ServiceDataImportErrorView("订单", 7, "支付宝账号", "INVALID_ACCOUNT",
                                "WARNING", "135***", "账号格式无效")), 21, 2, 20));

        mvc.perform(get("/api/v1/customer-service/imports/batch-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchId").value("batch-1"));
        String response = mvc.perform(get("/api/v1/customer-service/imports/batch-1/errors")
                        .param("page", "2").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(21))
                .andExpect(jsonPath("$.items[0].severity").value("WARNING"))
                .andExpect(jsonPath("$.items[0].maskedValue").value("135***"))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("13512345678", "token", "scene_major");
    }

    @Test
    void confirmRequiresAndForwardsAllOptimisticExpectations() throws Exception {
        when(confirm.confirm(any(), eq("batch-1"), any())).thenReturn(
                new ServiceDataImportConfirmationView("batch-1", "CONFIRMING", 5,
                        ServiceDataImportCounts.empty(), ServiceDataImportCounts.empty(),
                        ServiceDataImportCounts.empty()));
        String request = "{\"expectedSourceSha256\":\"" + SHA
                + "\",\"expectedParserVersion\":\"competition-workbook-v1\","
                + "\"expectedVersion\":4,\"warningsReviewed\":true}";

        mvc.perform(post("/api/v1/customer-service/imports/batch-1/confirm")
                        .contentType("application/json").content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMING"))
                .andExpect(jsonPath("$.version").value(5));

        ArgumentCaptor<ConfirmServiceDataImportCommand> command =
                ArgumentCaptor.forClass(ConfirmServiceDataImportCommand.class);
        verify(confirm).confirm(any(), eq("batch-1"), command.capture());
        assertThat(command.getValue().getExpectedVersion()).isEqualTo(4);
        assertThat(command.getValue().isWarningsReviewed()).isTrue();
    }

    @Test
    void confirmRejectsMissingExpectedVersionAtTheApiBoundary() throws Exception {
        String request = "{\"expectedSourceSha256\":\"" + SHA
                + "\",\"expectedParserVersion\":\"competition-workbook-v1\"}";

        mvc.perform(post("/api/v1/customer-service/imports/batch-1/confirm")
                        .contentType("application/json").content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()));
    }

    @Test
    void confirmRejectsUnknownFieldsFromTheFrozenRequestContract() throws Exception {
        String request = "{\"expectedSourceSha256\":\"" + SHA
                + "\",\"expectedParserVersion\":\"competition-workbook-v1\","
                + "\"expectedVersion\":4,\"rawToken\":\"super-secret\"}";

        String response = mvc.perform(post("/api/v1/customer-service/imports/batch-1/confirm")
                        .contentType("application/json").content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.PARAM_ERROR.getCode()))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("rawToken", "super-secret");
    }

    @Test
    void importBusinessErrorsUseContractStatusAndCanonicalSanitizedResponse() throws Exception {
        when(confirm.confirm(any(), eq("batch-1"), any())).thenThrow(
                new com.hmdp.exception.BusinessException(ErrorCode.CS_IMPORT_CONFLICT,
                        "token=super-secret account=13512345678"));
        String request = "{\"expectedSourceSha256\":\"" + SHA
                + "\",\"expectedParserVersion\":\"competition-workbook-v1\","
                + "\"expectedVersion\":4}";

        String response = mvc.perform(post("/api/v1/customer-service/imports/batch-1/confirm")
                        .contentType("application/json").content(request))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.CS_IMPORT_CONFLICT.getCode()))
                .andExpect(jsonPath("$.errorMsg")
                        .value(ErrorCode.CS_IMPORT_CONFLICT.getMessage()))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("super-secret", "13512345678");
    }

    @Test
    void everyImportEndpointUsesTheDataImportPermissionDeclaration() {
        RequireCustomerServicePermission permission =
                ServiceDataImportController.class.getAnnotation(RequireCustomerServicePermission.class);

        assertThat(permission).isNotNull();
        assertThat(permission.value()).isEqualTo(CustomerServicePermission.DATA_IMPORT);
        assertThat(ConfirmServiceDataImportRequest.class.getDeclaredFields())
                .extracting("name")
                .contains("expectedSourceSha256", "expectedParserVersion", "expectedVersion",
                        "warningsReviewed");
    }

    @Test
    void disabledMasterFeatureReturnsStableErrorBeforeControllerExecution() throws Exception {
        ServiceDataImportController controller =
                new ServiceDataImportController(preview, getBatch, getErrors, confirm);
        MockMvc disabledMvc = MockMvcBuilders.standaloneSetup(controller)
                .addFilters(new CustomerServiceFeatureGateFilter(false))
                .build();

        disabledMvc.perform(get("/api/v1/customer-service/imports/batch-1"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CS_FEATURE_DISABLED"));
    }

    private ServiceDataImportBatchView batch() {
        return new ServiceDataImportBatchView("batch-1", "data.xlsx", SHA,
                "competition-workbook-v1", new ServiceDataImportCounts(1, 2, 3, 4, 5, 6, 0),
                1, 0, true, "READY_TO_CONFIRM", EXPIRES, null, 4);
    }

    private static String xlsxMime() {
        return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    }
}
