package com.hmdp.servicedata.application.imports;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Full typed output of one workbook parse. Evaluation label columns never appear in
 * any row; the parser drops them at the cell level and only reports how many such
 * columns were seen (names are part of the frozen deny-list, so listing them here
 * does not leak values).
 */
public final class WorkbookParseResult {
    private final String parserVersion;
    private final List<ImportRows.ConversationRow> conversations = new ArrayList<>();
    private final List<ImportRows.MessageRow> messages = new ArrayList<>();
    private final List<ImportRows.OrderRow> orders = new ArrayList<>();
    private final List<ImportRows.ServiceCaseRow> serviceCases = new ArrayList<>();
    private final List<ImportRows.SourceLinkRow> links = new ArrayList<>();
    private final List<ImportRows.ConsumerAliasRow> aliases = new ArrayList<>();
    private final List<ImportIssue> issues = new ArrayList<>();
    private final Set<String> droppedLabelColumns = new LinkedHashSet<>();
    private final Set<String> unknownColumns = new LinkedHashSet<>();
    private int missingMediaCount;

    public WorkbookParseResult(String parserVersion) {
        this.parserVersion = parserVersion;
    }

    public String getParserVersion() {
        return parserVersion;
    }

    public List<ImportRows.ConversationRow> getConversations() {
        return conversations;
    }

    public List<ImportRows.MessageRow> getMessages() {
        return messages;
    }

    public List<ImportRows.OrderRow> getOrders() {
        return orders;
    }

    public List<ImportRows.ServiceCaseRow> getServiceCases() {
        return serviceCases;
    }

    public List<ImportRows.SourceLinkRow> getLinks() {
        return links;
    }

    public List<ImportRows.ConsumerAliasRow> getAliases() {
        return aliases;
    }

    public List<ImportIssue> getIssues() {
        return issues;
    }

    public Set<String> getDroppedLabelColumns() {
        return Collections.unmodifiableSet(droppedLabelColumns);
    }

    public void recordDroppedLabelColumn(String normalizedName) {
        droppedLabelColumns.add(normalizedName);
    }

    public Set<String> getUnknownColumns() {
        return Collections.unmodifiableSet(unknownColumns);
    }

    public void recordUnknownColumn(String sheet, String normalizedName) {
        unknownColumns.add(sheet + "!" + normalizedName);
    }

    public int getMissingMediaCount() {
        return missingMediaCount;
    }

    public void incrementMissingMedia() {
        missingMediaCount++;
    }

    public long blockingIssueCount() {
        return issues.stream().filter(ImportIssue::isBlocking).count();
    }

    public long warningIssueCount() {
        return issues.stream().filter(issue -> !issue.isBlocking()).count();
    }
}
