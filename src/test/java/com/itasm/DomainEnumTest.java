package com.itasm;

import com.itasm.domain.AssetStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DomainEnumTest {

    @Test
    void assetStatusesMatchSpecification() {
        assertEquals(6, AssetStatus.values().length);
        AssetStatus.valueOf("PROCURED");
        AssetStatus.valueOf("RECEIVED");
        AssetStatus.valueOf("AVAILABLE");
        AssetStatus.valueOf("ASSIGNED");
        AssetStatus.valueOf("REPAIR");
        AssetStatus.valueOf("RETIRED");
    }
}
