package com.hmdp.servicedata.application.port.in;

import com.hmdp.servicedata.application.contract.ServiceDataImportBatchView;
import com.hmdp.servicedata.application.contract.ServiceDataImportScope;
import com.hmdp.servicedata.application.contract.ServiceDataImportUpload;

public interface PreviewServiceDataImportUseCase {
    ServiceDataImportBatchView preview(ServiceDataImportScope scope, ServiceDataImportUpload upload);
}
