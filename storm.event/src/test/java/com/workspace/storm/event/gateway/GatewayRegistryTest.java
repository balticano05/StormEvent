package com.workspace.storm.event.gateway;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.List;

class GatewayRegistryTest {

    private static SourceGateway stub(String id, SourceKind kind) {
        return new SourceGateway() {
            @Override public String sourceId() { return id; }
            @Override public SourceKind kind() { return kind; }
            @Override public boolean supportsSuggest() { return false; }
        };
    }

    @Test
    void registersUniqueGateways() {
        GatewayRegistry registry = new GatewayRegistry(List.of(
                stub("atlasbus", SourceKind.BUS), stub("bzd", SourceKind.TRAIN)));
        assertEquals(2, registry.size());
        assertNotNull(registry.get("atlasbus"));
    }

    @Test
    void rejectsDuplicates() {
        assertThrows(IllegalStateException.class, () ->
                new GatewayRegistry(List.of(stub("bzd", SourceKind.TRAIN), stub("bzd", SourceKind.TRAIN))));
    }
}
