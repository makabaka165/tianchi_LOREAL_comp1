package com.hmdp.servicedata.api;

import com.hmdp.security.customer.CustomerServicePermission;
import com.hmdp.security.customer.CustomerServiceScopeContext;
import com.hmdp.security.customer.CustomerServiceScopeContextHolder;
import com.hmdp.security.customer.RequireCustomerServicePermission;
import com.hmdp.servicedata.api.dto.ConfirmServiceDataImportRequest;
import com.hmdp.servicedata.application.contract.ConfirmServiceDataImportCommand;
import com.hmdp.servicedata.application.contract.ServiceDataImportBatchView;
import com.hmdp.servicedata.application.contract.ServiceDataImportConfirmationView;
import com.hmdp.servicedata.application.contract.ServiceDataImportErrorPage;
import com.hmdp.servicedata.application.contract.ServiceDataImportScope;
import com.hmdp.servicedata.application.contract.ServiceDataImportUpload;
import com.hmdp.servicedata.application.port.in.ConfirmServiceDataImportUseCase;
import com.hmdp.servicedata.application.port.in.GetServiceDataImportBatchUseCase;
import com.hmdp.servicedata.application.port.in.GetServiceDataImportErrorsUseCase;
import com.hmdp.servicedata.application.port.in.PreviewServiceDataImportUseCase;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/customer-service/imports")
@RequireCustomerServicePermission(CustomerServicePermission.DATA_IMPORT)
@Validated
public class ServiceDataImportController {
    private final PreviewServiceDataImportUseCase preview;
    private final GetServiceDataImportBatchUseCase getBatch;
    private final GetServiceDataImportErrorsUseCase getErrors;
    private final ConfirmServiceDataImportUseCase confirm;

    public ServiceDataImportController(PreviewServiceDataImportUseCase preview,
                                       GetServiceDataImportBatchUseCase getBatch,
                                       GetServiceDataImportErrorsUseCase getErrors,
                                       ConfirmServiceDataImportUseCase confirm) {
        this.preview = preview;
        this.getBatch = getBatch;
        this.getErrors = getErrors;
        this.confirm = confirm;
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ServiceDataImportBatchView preview(@RequestParam("file") MultipartFile file) {
        ServiceDataImportUpload upload = new ServiceDataImportUpload(file.getOriginalFilename(),
                file.getContentType(), file.getSize(), file::getInputStream);
        return preview.preview(scope(), upload);
    }

    @GetMapping("/{batchId}")
    public ServiceDataImportBatchView getBatch(
            @PathVariable @Size(min = 1, max = 64) String batchId) {
        return getBatch.getBatch(scope(), batchId);
    }

    @GetMapping("/{batchId}/errors")
    public ServiceDataImportErrorPage getErrors(
            @PathVariable @Size(min = 1, max = 64) String batchId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
        return getErrors.getErrors(scope(), batchId, page, size);
    }

    @PostMapping(value = "/{batchId}/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ServiceDataImportConfirmationView confirm(
            @PathVariable @Size(min = 1, max = 64) String batchId,
            @Valid @RequestBody ConfirmServiceDataImportRequest request) {
        ConfirmServiceDataImportCommand command = new ConfirmServiceDataImportCommand(
                request.getExpectedSourceSha256(), request.getExpectedParserVersion(),
                request.getExpectedVersion(), request.isWarningsReviewed());
        return confirm.confirm(scope(), batchId, command);
    }

    private ServiceDataImportScope scope() {
        CustomerServiceScopeContext context = CustomerServiceScopeContextHolder.require();
        return new ServiceDataImportScope(context.getTenantId(), context.getWorkspaceId(),
                context.getUserId());
    }
}
