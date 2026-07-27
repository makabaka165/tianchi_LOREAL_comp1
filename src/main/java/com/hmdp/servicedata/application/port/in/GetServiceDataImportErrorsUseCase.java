package com.hmdp.servicedata.application.port.in;

import com.hmdp.servicedata.application.contract.ServiceDataImportErrorPage;
import com.hmdp.servicedata.application.contract.ServiceDataImportScope;

public interface GetServiceDataImportErrorsUseCase {
    ServiceDataImportErrorPage getErrors(ServiceDataImportScope scope, String batchId,
                                         int page, int size);
}
