package com.hmdp.servicedata.application.port.out;

import com.hmdp.servicedata.application.contract.ServiceDataImportCounts;
import com.hmdp.servicedata.application.contract.ServiceDataImportErrorPage;
import com.hmdp.servicedata.application.imports.WorkbookParseResult;
import com.hmdp.servicedata.domain.model.ScopeRef;

import java.time.Instant;

public interface ImportStagingPort {
    void savePreview(ScopeRef scope, String batchId, Instant expiresAt,
                     WorkbookParseResult result, ServiceDataImportCounts previewCounts);

    ServiceDataImportCounts findCounts(ScopeRef scope, String batchId);

    ServiceDataImportErrorPage findErrors(ScopeRef scope, String batchId, int page, int size);
}
