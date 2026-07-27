package com.hmdp.servicedata.application.contract;

/** Optimistic confirmation expectations captured from the preview the operator saw. */
public final class ConfirmServiceDataImportCommand {
    private final String expectedSourceSha256;
    private final String expectedParserVersion;
    private final int expectedVersion;
    private final boolean warningsReviewed;

    public ConfirmServiceDataImportCommand(String expectedSourceSha256,
                                           String expectedParserVersion,
                                           int expectedVersion,
                                           boolean warningsReviewed) {
        this.expectedSourceSha256 = expectedSourceSha256;
        this.expectedParserVersion = expectedParserVersion;
        this.expectedVersion = expectedVersion;
        this.warningsReviewed = warningsReviewed;
    }

    public String getExpectedSourceSha256() {
        return expectedSourceSha256;
    }

    public String getExpectedParserVersion() {
        return expectedParserVersion;
    }

    public int getExpectedVersion() {
        return expectedVersion;
    }

    public boolean isWarningsReviewed() {
        return warningsReviewed;
    }
}
