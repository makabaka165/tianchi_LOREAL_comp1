package com.hmdp.servicedata.application.contract;

import com.hmdp.servicedata.application.imports.ImportIssue;

/** One masked warning or blocking error in the paged import report. */
public final class ServiceDataImportErrorView {
    private final String sheet;
    private final int row;
    private final String field;
    private final String errorCode;
    private final String severity;
    private final String maskedValue;
    private final String message;

    public ServiceDataImportErrorView(String sheet, int row, String field, String errorCode,
                                      String severity, String maskedValue, String message) {
        this.sheet = sheet;
        this.row = row;
        this.field = field;
        this.errorCode = errorCode;
        this.severity = severity;
        this.maskedValue = maskedValue;
        this.message = message;
    }

    public static ServiceDataImportErrorView from(ImportIssue issue) {
        return new ServiceDataImportErrorView(issue.getSheet(), issue.getRowNo(),
                issue.getField(), issue.getErrorCode(), issue.getSeverity().name(),
                issue.getMaskedValue(), issue.getMessage());
    }

    public String getSheet() {
        return sheet;
    }

    public int getRow() {
        return row;
    }

    public String getField() {
        return field;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getSeverity() {
        return severity;
    }

    public String getMaskedValue() {
        return maskedValue;
    }

    public String getMessage() {
        return message;
    }
}
