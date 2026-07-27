package com.hmdp.servicedata.domain.repository;

import com.hmdp.servicedata.domain.model.Consumer;
import com.hmdp.servicedata.domain.model.ScopeRef;

import java.util.Optional;

public interface ConsumerRepository {
    void insertIfAbsent(Consumer consumer, String actor);

    Optional<Consumer> findById(ScopeRef scope, String consumerId);
}
