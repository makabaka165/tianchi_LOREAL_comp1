package com.hmdp.servicedata.application.contract;

import com.hmdp.servicedata.application.imports.ImportIssue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/** Stable one-based page of masked import issues. */
public final class ServiceDataImportErrorPage {
    private final List<ServiceDataImportErrorView> items;
    private final long total;
    private final int page;
    private final int size;

    public ServiceDataImportErrorPage(List<ServiceDataImportErrorView> items, long total,
                                      int page, int size) {
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
        this.total = total;
        this.page = page;
        this.size = size;
    }

    public static ServiceDataImportErrorPage fromIssues(List<ImportIssue> issues, long total,
                                                        int page, int size) {
        return new ServiceDataImportErrorPage(issues.stream()
                .map(ServiceDataImportErrorView::from)
                .collect(Collectors.toList()), total, page, size);
    }

    public List<ServiceDataImportErrorView> getItems() {
        return items;
    }

    public long getTotal() {
        return total;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }
}
