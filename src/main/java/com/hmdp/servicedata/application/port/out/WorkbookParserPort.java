package com.hmdp.servicedata.application.port.out;

import com.hmdp.servicedata.application.imports.WorkbookParseResult;

import java.io.IOException;
import java.io.InputStream;

public interface WorkbookParserPort {
    String parserVersion();

    WorkbookParseResult parse(InputStream input, long declaredSize) throws IOException;
}
