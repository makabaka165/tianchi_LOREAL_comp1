package com.hmdp.servicedata.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.servicedata.application.imports.ImportRows;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ImportStagingPayloadSerializationTest {

    @Test
    void typedRowsRoundTripWithoutAnUntypedApplicationBoundary() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        ImportRows.OrderRow source = new ImportRows.OrderRow("000123", "S00001", "方***",
                "orders", "sku-1", "商品", 2, BigDecimal.ONE, BigDecimal.TEN, "PAID",
                Instant.parse("2026-07-27T04:00:00Z"), null, null, null, null,
                Map.of("赠品", "试用装"), 7);

        String json = mapper.writeValueAsString(source);
        ImportRows.OrderRow restored = mapper.readValue(json, ImportRows.OrderRow.class);

        assertThat(restored.orderNo).isEqualTo("000123");
        assertThat(restored.paidAmount).isEqualByComparingTo("10");
        assertThat(restored.detail).containsEntry("赠品", "试用装");
        assertThat(json).doesNotContain("scene_major", "scene_minor", "target");
    }
}
