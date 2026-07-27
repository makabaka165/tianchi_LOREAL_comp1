package com.hmdp.servicedata.domain.repository;

import com.hmdp.servicedata.domain.model.ConsumerAlias;
import com.hmdp.servicedata.domain.model.ScopeRef;

import java.util.Optional;

public interface ConsumerAliasRepository {
    boolean insertIfAbsent(ConsumerAlias alias);

    Optional<ConsumerAlias> findByIdentity(ScopeRef scope, String sourceSystem,
                                           String sourceScope, String normalizedAliasHash);

    long countByScope(ScopeRef scope);
}
