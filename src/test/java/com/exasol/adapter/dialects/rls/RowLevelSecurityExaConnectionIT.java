package com.exasol.adapter.dialects.rls;

import java.util.Map;

import org.junit.jupiter.api.Tag;

@Tag("integration")
@Tag("virtual-schema")
class RowLevelSecurityExaConnectionIT extends AbstractRowLevelSecurityIT {
    @Override
    protected Map<String, String> getVirtualSchemaProperties() {
        return Map.of("IMPORT_FROM_EXA", "true", "EXA_CONNECTION_STRING", "localhost:8888");
    }
}