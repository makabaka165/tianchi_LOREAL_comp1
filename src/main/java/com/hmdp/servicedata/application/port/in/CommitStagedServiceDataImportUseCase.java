package com.hmdp.servicedata.application.port.in;

import com.hmdp.servicedata.application.contract.ServiceDataImportCommitSummary;
import com.hmdp.servicedata.domain.model.ScopeRef;

public interface CommitStagedServiceDataImportUseCase {
    ServiceDataImportCommitSummary commit(ScopeRef scope, String batchId, String actor);
}
