package com.hmdp.servicedata.application.contract;

import com.hmdp.servicedata.application.imports.WorkbookParseResult;

/** Explicit preview counts. Dynamic maps do not cross the application or API boundary. */
public final class ServiceDataImportCounts {
    private final int consumerAliases;
    private final int conversations;
    private final int messages;
    private final int orderSnapshots;
    private final int serviceCases;
    private final int sourceLinks;
    private final int missingMedia;

    public ServiceDataImportCounts(int consumerAliases, int conversations, int messages,
                                   int orderSnapshots, int serviceCases, int sourceLinks,
                                   int missingMedia) {
        this.consumerAliases = nonNegative(consumerAliases, "consumerAliases");
        this.conversations = nonNegative(conversations, "conversations");
        this.messages = nonNegative(messages, "messages");
        this.orderSnapshots = nonNegative(orderSnapshots, "orderSnapshots");
        this.serviceCases = nonNegative(serviceCases, "serviceCases");
        this.sourceLinks = nonNegative(sourceLinks, "sourceLinks");
        this.missingMedia = nonNegative(missingMedia, "missingMedia");
    }

    public static ServiceDataImportCounts from(WorkbookParseResult result) {
        return new ServiceDataImportCounts(result.getAliases().size(),
                result.getConversations().size(), result.getMessages().size(),
                result.getOrders().size(), result.getServiceCases().size(),
                result.getLinks().size(), result.getMissingMediaCount());
    }

    public static ServiceDataImportCounts empty() {
        return new ServiceDataImportCounts(0, 0, 0, 0, 0, 0, 0);
    }

    private static int nonNegative(int value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " must not be negative");
        }
        return value;
    }

    public int total() {
        return consumerAliases + conversations + messages + orderSnapshots
                + serviceCases + sourceLinks;
    }

    public int getConsumerAliases() {
        return consumerAliases;
    }

    public int getConversations() {
        return conversations;
    }

    public int getMessages() {
        return messages;
    }

    public int getOrderSnapshots() {
        return orderSnapshots;
    }

    public int getServiceCases() {
        return serviceCases;
    }

    public int getSourceLinks() {
        return sourceLinks;
    }

    public int getMissingMedia() {
        return missingMedia;
    }
}
