package com.hmdp.servicedata.application.imports;

import com.hmdp.servicedata.application.contract.ServiceDataImportCounts;
import com.hmdp.servicedata.domain.model.Message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Typed rows read back from one scoped staging batch for atomic commit. */
public final class StagedImportRows {
    private final List<ImportRows.ConsumerAliasRow> aliases = new ArrayList<>();
    private final List<ImportRows.ConversationRow> conversations = new ArrayList<>();
    private final List<ImportRows.MessageRow> messages = new ArrayList<>();
    private final List<ImportRows.OrderRow> orders = new ArrayList<>();
    private final List<ImportRows.ServiceCaseRow> serviceCases = new ArrayList<>();
    private final List<ImportRows.SourceLinkRow> links = new ArrayList<>();

    public List<ImportRows.ConsumerAliasRow> getAliases() {
        return Collections.unmodifiableList(aliases);
    }

    public List<ImportRows.ConversationRow> getConversations() {
        return Collections.unmodifiableList(conversations);
    }

    public List<ImportRows.MessageRow> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    public List<ImportRows.OrderRow> getOrders() {
        return Collections.unmodifiableList(orders);
    }

    public List<ImportRows.ServiceCaseRow> getServiceCases() {
        return Collections.unmodifiableList(serviceCases);
    }

    public List<ImportRows.SourceLinkRow> getLinks() {
        return Collections.unmodifiableList(links);
    }

    public void addAlias(ImportRows.ConsumerAliasRow row) {
        aliases.add(row);
    }

    public void addConversation(ImportRows.ConversationRow row) {
        conversations.add(row);
    }

    public void addMessage(ImportRows.MessageRow row) {
        messages.add(row);
    }

    public void addOrder(ImportRows.OrderRow row) {
        orders.add(row);
    }

    public void addServiceCase(ImportRows.ServiceCaseRow row) {
        serviceCases.add(row);
    }

    public void addLink(ImportRows.SourceLinkRow row) {
        links.add(row);
    }

    public ServiceDataImportCounts counts() {
        int missingMedia = (int) messages.stream()
                .filter(row -> Message.MEDIA_STATUS_MISSING.equals(row.mediaStatus))
                .count();
        return new ServiceDataImportCounts(aliases.size(), conversations.size(), messages.size(),
                orders.size(), serviceCases.size(), links.size(), missingMedia);
    }
}
