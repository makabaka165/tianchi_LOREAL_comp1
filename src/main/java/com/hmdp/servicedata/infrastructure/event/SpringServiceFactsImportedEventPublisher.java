package com.hmdp.servicedata.infrastructure.event;

import com.hmdp.servicedata.application.event.ServiceFactsImported;
import com.hmdp.servicedata.application.port.out.ServiceFactsImportedEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Publishes after commit and prevents listener failures from changing committed facts. */
@Component
public class SpringServiceFactsImportedEventPublisher
        implements ServiceFactsImportedEventPublisher {
    private static final Logger LOG = LoggerFactory.getLogger(
            SpringServiceFactsImportedEventPublisher.class);

    private final ApplicationEventPublisher events;

    public SpringServiceFactsImportedEventPublisher(ApplicationEventPublisher events) {
        this.events = events;
    }

    @Override
    public void publishAfterCommit(ServiceFactsImported event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            publishSafely(event);
                        }
                    });
            return;
        }
        publishSafely(event);
    }

    private void publishSafely(ServiceFactsImported event) {
        try {
            events.publishEvent(event);
        } catch (RuntimeException failure) {
            LOG.warn("ServiceFactsImported listener failed for batchId={} tenantId={} workspaceId={}",
                    event.getBatchId(), event.getTenantId(), event.getWorkspaceId());
        }
    }
}
