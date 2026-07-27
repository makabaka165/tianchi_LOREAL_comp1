package com.hmdp.servicedata.api.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

public class ConfirmServiceDataImportRequest {
    @NotBlank(message = "expectedSourceSha256 is required")
    @Pattern(regexp = "^[0-9a-fA-F]{64}$", message = "expectedSourceSha256 is invalid")
    private String expectedSourceSha256;

    @NotBlank(message = "expectedParserVersion is required")
    @Size(max = 32, message = "expectedParserVersion is too long")
    private String expectedParserVersion;

    @NotNull(message = "expectedVersion is required")
    @Min(value = 0, message = "expectedVersion must not be negative")
    private Integer expectedVersion;

    private boolean warningsReviewed;

    public String getExpectedSourceSha256() {
        return expectedSourceSha256;
    }

    public void setExpectedSourceSha256(String expectedSourceSha256) {
        this.expectedSourceSha256 = expectedSourceSha256;
    }

    public String getExpectedParserVersion() {
        return expectedParserVersion;
    }

    public void setExpectedParserVersion(String expectedParserVersion) {
        this.expectedParserVersion = expectedParserVersion;
    }

    public Integer getExpectedVersion() {
        return expectedVersion;
    }

    public void setExpectedVersion(Integer expectedVersion) {
        this.expectedVersion = expectedVersion;
    }

    public boolean isWarningsReviewed() {
        return warningsReviewed;
    }

    public void setWarningsReviewed(boolean warningsReviewed) {
        this.warningsReviewed = warningsReviewed;
    }

    @JsonAnySetter
    public void rejectUnknownField(String fieldName, JsonNode ignoredValue) {
        throw new IllegalArgumentException("unsupported confirm request field");
    }
}
