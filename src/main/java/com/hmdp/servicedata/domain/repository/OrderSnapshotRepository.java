package com.hmdp.servicedata.domain.repository;

import com.hmdp.servicedata.domain.model.OrderSnapshot;
import com.hmdp.servicedata.domain.model.ScopeRef;

public interface OrderSnapshotRepository {
    void insert(OrderSnapshot snapshot);

    boolean existsByContent(ScopeRef scope, String orderNo, String contentHash);

    boolean existsByOrderNo(ScopeRef scope, String orderNo);

    int nextSnapshotSeq(ScopeRef scope, String orderNo);

    long countByScope(ScopeRef scope);
}
