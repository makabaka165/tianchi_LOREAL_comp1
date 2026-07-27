package com.hmdp.servicedata.domain.repository;

import com.hmdp.servicedata.domain.model.ScopeRef;
import com.hmdp.servicedata.domain.model.ServiceCase;

public interface ServiceCaseRepository {
    void insert(ServiceCase serviceCase);

    boolean existsByContent(ScopeRef scope, String caseNo, String contentHash);

    boolean existsByCaseNo(ScopeRef scope, String caseNo);

    int nextCaseSeq(ScopeRef scope, String caseNo);

    long countByScope(ScopeRef scope);
}
