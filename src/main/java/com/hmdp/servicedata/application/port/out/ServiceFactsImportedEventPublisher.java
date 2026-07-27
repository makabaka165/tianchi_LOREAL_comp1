package com.hmdp.servicedata.application.port.out;

import com.hmdp.servicedata.application.event.ServiceFactsImported;

/** Schedules a PII-free event for publication only after the current transaction commits. */
public interface ServiceFactsImportedEventPublisher {
    void publishAfterCommit(ServiceFactsImported event);
}
