package com.hmdp.servicedata.application.port.in;

import com.hmdp.servicedata.application.contract.ConfirmServiceDataImportCommand;
import com.hmdp.servicedata.application.contract.ServiceDataImportConfirmationView;
import com.hmdp.servicedata.application.contract.ServiceDataImportScope;

public interface ConfirmServiceDataImportUseCase {
    ServiceDataImportConfirmationView confirm(ServiceDataImportScope scope, String batchId,
                                               ConfirmServiceDataImportCommand command);
}
