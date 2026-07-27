package com.hmdp.servicedata.infrastructure.persistence;

import java.sql.Timestamp;
import java.time.Instant;

final class JdbcServiceDataSupport {
    private JdbcServiceDataSupport() {
    }

    static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
