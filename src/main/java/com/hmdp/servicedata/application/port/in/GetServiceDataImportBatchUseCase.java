package com.hmdp.servicedata.application.port.in;

import com.hmdp.servicedata.application.contract.ServiceDataImportBatchView;
import com.hmdp.servicedata.application.contract.ServiceDataImportScope;

public interface GetServiceDataImportBatchUseCase {
    ServiceDataImportBatchView getBatch(ServiceDataImportScope scope, String batchId);
}
