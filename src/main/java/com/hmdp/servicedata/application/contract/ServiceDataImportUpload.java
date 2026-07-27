package com.hmdp.servicedata.application.contract;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/** Repeatable upload stream plus transport metadata; independent of Spring MultipartFile. */
public final class ServiceDataImportUpload {
    @FunctionalInterface
    public interface InputStreamOpener {
        InputStream open() throws IOException;
    }

    private final String fileName;
    private final String contentType;
    private final long declaredSize;
    private final InputStreamOpener opener;

    public ServiceDataImportUpload(String fileName, String contentType, long declaredSize,
                                   InputStreamOpener opener) {
        this.fileName = fileName;
        this.contentType = contentType;
        this.declaredSize = declaredSize;
        this.opener = Objects.requireNonNull(opener, "opener");
    }

    public InputStream openStream() throws IOException {
        return opener.open();
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getDeclaredSize() {
        return declaredSize;
    }
}
