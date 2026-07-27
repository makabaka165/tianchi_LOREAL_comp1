package com.hmdp.servicedata.infrastructure.event;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.hmdp.servicedata.application.contract.ServiceDataImportCounts;
import com.hmdp.servicedata.application.event.ServiceFactsImported;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class SpringServiceFactsImportedEventPublisherTest {

    @AfterEach
    void clearTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void publishesOnlyAfterCommit() {
        List<Object> published = new ArrayList<>();
        SpringServiceFactsImportedEventPublisher publisher =
                new SpringServiceFactsImportedEventPublisher(published::add);
        beginTransactionSynchronization();

        publisher.publishAfterCommit(event());

        assertThat(published).isEmpty();
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        assertThat(published).singleElement().isInstanceOf(ServiceFactsImported.class);
    }

    @Test
    void listenerFailureIsContainedWithoutLoggingItsSensitiveMessage() {
        SpringServiceFactsImportedEventPublisher publisher =
                new SpringServiceFactsImportedEventPublisher(event -> {
                    throw new IllegalStateException("token=secret consumer=raw-value");
                });
        Logger logger = (Logger) LoggerFactory.getLogger(
                SpringServiceFactsImportedEventPublisher.class);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);

        try {
            assertThatCode(() -> publisher.publishAfterCommit(event())).doesNotThrowAnyException();
            String rendered = String.join("\n", logs.list.stream()
                    .map(ILoggingEvent::getFormattedMessage).toArray(String[]::new));
            assertThat(rendered).contains("batch-1")
                    .doesNotContain("token=secret", "raw-value");
        } finally {
            logger.detachAppender(logs);
        }
    }

    private void beginTransactionSynchronization() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    private ServiceFactsImported event() {
        ServiceDataImportCounts zero = ServiceDataImportCounts.empty();
        return new ServiceFactsImported("batch-1", "tenant-a", "workspace-a",
                zero, zero, zero, Instant.parse("2026-07-27T05:00:00Z"));
    }
}
