package com.hmdp.servicedata.domain.model;

/** Confirmation preconditions no longer hold; surfaces as CS_IMPORT_CONFLICT. */
public class ImportConflictException extends RuntimeException {
    public ImportConflictException(String message) {
        super(message);
    }
}
