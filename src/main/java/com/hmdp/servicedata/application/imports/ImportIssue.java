package com.hmdp.servicedata.application.imports;

import com.hmdp.servicedata.domain.model.ImportErrorSeverity;
import com.hmdp.servicedata.domain.model.ScopeRef;

import java.util.Objects;

/**
 * One validation finding located by sheet/row/field. Raw values are always masked
 * before they enter an issue; account numbers, phones and addresses never appear
 * verbatim in error reports or logs.
 */
public final class ImportIssue {
    private final String sheet;
    private final int rowNo;
    private final String field;
    private final String errorCode;
    private final ImportErrorSeverity severity;
    private final String maskedValue;
    private final String message;

    public ImportIssue(String sheet, int rowNo, String field, String errorCode,
                       ImportErrorSeverity severity, String maskedValue, String message) {
        this.sheet = ScopeRef.requireText(sheet, "sheet");
        this.rowNo = rowNo;
        this.field = field;
        this.errorCode = ScopeRef.requireText(errorCode, "errorCode");
        this.severity = Objects.requireNonNull(severity, "severity");
        this.maskedValue = maskedValue;
        this.message = ScopeRef.requireText(message, "message");
    }

    public String getSheet() {
        return sheet;
    }

    public int getRowNo() {
        return rowNo;
    }

    public String getField() {
        return field;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public ImportErrorSeverity getSeverity() {
        return severity;
    }

    public String getMaskedValue() {
        return maskedValue;
    }

    public String getMessage() {
        return message;
    }

    public boolean isBlocking() {
        return severity == ImportErrorSeverity.BLOCKING;
    }

    @Override
    public String toString() {
        return sheet + "!" + rowNo + " " + errorCode + "(" + severity + ")";
    }
}
