package com.hmdp.servicedata.infrastructure.parser;

import com.hmdp.servicedata.application.imports.WorkbookParseResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opt-in smoke test against the official competition workbook. Runs only when
 * HMDP_CS_SOURCE_ROOT points at the local material directory (never in CI; the raw
 * file is never committed). Verifies the M1 baseline counts from the execution plan:
 * 138 conversations, 998 messages, 112 aliases, 113 orders, 80 service cases and
 * 29 missing-media references — and that no evaluation label survives parsing.
 */
@Tag("external")
class OfficialWorkbookParserSmokeTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "HMDP_CS_SOURCE_ROOT", matches = ".+")
    void officialWorkbookMatchesBaselineCounts() throws Exception {
        Path workbook = Path.of(System.getenv("HMDP_CS_SOURCE_ROOT"),
                "赛题 1：数据共情者-业务数据.xlsx");
        assertThat(workbook).exists();

        WorkbookParseResult result;
        try (InputStream in = Files.newInputStream(workbook)) {
            result = new CompetitionWorkbookParser().parse(in, Files.size(workbook));
        }

        assertThat(result.getConversations()).hasSize(138);
        assertThat(result.getMessages()).hasSize(998);
        assertThat(result.getAliases()).hasSize(112);
        assertThat(result.getOrders()).hasSize(113);
        assertThat(result.getServiceCases()).hasSize(80);
        assertThat(result.getMissingMediaCount()).isEqualTo(29);
        assertThat(result.blockingIssueCount()).isZero();
        // three presale orders carry "（定金）"-annotated payment times: parsed with a warning
        assertThat(result.getIssues())
                .filteredOn(issue -> "ANNOTATED_DATETIME".equals(issue.getErrorCode()))
                .hasSize(3);

        assertThat(result.getDroppedLabelColumns())
                .contains("scene_major", "scene_minor", "is_target_buyer_message");
        String allText = result.getMessages().stream()
                .map(m -> m.content == null ? "" : m.content)
                .reduce("", String::concat);
        assertThat(allText).doesNotContain("scene_major", "scene_minor");
    }
}
