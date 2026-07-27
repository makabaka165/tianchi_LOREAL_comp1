package com.hmdp.servicedata.application.service;

/** A staged source invariant prevents an atomic fact commit. */
public final class ServiceDataImportCommitException extends RuntimeException {
    public ServiceDataImportCommitException(String message) {
        super(message);
    }
}
