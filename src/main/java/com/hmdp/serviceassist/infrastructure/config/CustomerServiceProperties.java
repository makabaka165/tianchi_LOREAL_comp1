package com.hmdp.serviceassist.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Feature flags and budgets for the customer-service vertical. All flags default to
 * {@code false} so the local/test/prod profiles keep their current behaviour; the
 * competition profile enables them explicitly. The competition source directory is
 * configured through {@code HMDP_CS_SOURCE_ROOT}; code must never hardcode local paths.
 */
@ConfigurationProperties(prefix = "hmdp.customer-service")
public class CustomerServiceProperties {

    /** Master switch; when false every customer-service API returns CS_FEATURE_DISABLED. */
    private boolean enabled = false;

    /** Local directory containing the original competition workbook; never committed. */
    private String sourceRoot = "";

    private final Import imports = new Import();
    private final Assistance assistance = new Assistance();
    private final Risk risk = new Risk();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSourceRoot() {
        return sourceRoot;
    }

    public void setSourceRoot(String sourceRoot) {
        this.sourceRoot = sourceRoot == null ? "" : sourceRoot;
    }

    public Import getImport() {
        return imports;
    }

    public Assistance getAssistance() {
        return assistance;
    }

    public Risk getRisk() {
        return risk;
    }

    public enum AssistanceMode {
        /** Real model calls through the agent platform. */
        LIVE,
        /** Deterministic conservative suggestions computed from typed facts only. */
        DETERMINISTIC_FALLBACK,
        /**
         * Versioned, provenance-tagged offline fixtures. Allowed only in the competition
         * profile; production startup fails when this mode is configured.
         */
        DEMO_FIXTURE
    }

    public static class Import {
        private boolean enabled = false;
        private int stagingTtlHours = 24;
        private long maxFileSizeBytes = 20L * 1024 * 1024;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getStagingTtlHours() {
            return stagingTtlHours;
        }

        public void setStagingTtlHours(int stagingTtlHours) {
            this.stagingTtlHours = stagingTtlHours;
        }

        public long getMaxFileSizeBytes() {
            return maxFileSizeBytes;
        }

        public void setMaxFileSizeBytes(long maxFileSizeBytes) {
            this.maxFileSizeBytes = maxFileSizeBytes;
        }
    }

    public static class Assistance {
        private boolean enabled = false;
        private AssistanceMode mode = AssistanceMode.LIVE;
        private boolean fallbackEnabled = true;
        private long completionScanDelayMs = 5000;
        private int modelTimeoutSeconds = 30;
        private int maxOutputTokens = 1600;
        private int maxConcurrentRuns = 4;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public AssistanceMode getMode() {
            return mode;
        }

        public void setMode(AssistanceMode mode) {
            this.mode = mode == null ? AssistanceMode.LIVE : mode;
        }

        public boolean isFallbackEnabled() {
            return fallbackEnabled;
        }

        public void setFallbackEnabled(boolean fallbackEnabled) {
            this.fallbackEnabled = fallbackEnabled;
        }

        public long getCompletionScanDelayMs() {
            return completionScanDelayMs;
        }

        public void setCompletionScanDelayMs(long completionScanDelayMs) {
            this.completionScanDelayMs = completionScanDelayMs;
        }

        public int getModelTimeoutSeconds() {
            return modelTimeoutSeconds;
        }

        public void setModelTimeoutSeconds(int modelTimeoutSeconds) {
            this.modelTimeoutSeconds = modelTimeoutSeconds;
        }

        public int getMaxOutputTokens() {
            return maxOutputTokens;
        }

        public void setMaxOutputTokens(int maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
        }

        public int getMaxConcurrentRuns() {
            return maxConcurrentRuns;
        }

        public void setMaxConcurrentRuns(int maxConcurrentRuns) {
            this.maxConcurrentRuns = maxConcurrentRuns;
        }
    }

    public static class Risk {
        private boolean enabled = false;
        private boolean slaScanEnabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isSlaScanEnabled() {
            return slaScanEnabled;
        }

        public void setSlaScanEnabled(boolean slaScanEnabled) {
            this.slaScanEnabled = slaScanEnabled;
        }
    }
}
