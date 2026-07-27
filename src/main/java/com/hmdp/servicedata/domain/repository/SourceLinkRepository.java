package com.hmdp.servicedata.domain.repository;

import com.hmdp.servicedata.domain.model.ScopeRef;
import com.hmdp.servicedata.domain.model.SourceLink;
import com.hmdp.servicedata.domain.model.SourceLinkType;

public interface SourceLinkRepository {
    void insert(SourceLink link);

    boolean exists(ScopeRef scope, SourceLinkType type, String fromId, String toRef);

    long countByScope(ScopeRef scope);
}
