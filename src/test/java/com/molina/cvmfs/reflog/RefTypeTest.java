package com.molina.cvmfs.reflog;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RefTypeTest {

    @Test
    void fromValueAll() {
        assertEquals(RefType.CATALOG, RefType.fromValue(0));
        assertEquals(RefType.CERTIFICATE, RefType.fromValue(1));
        assertEquals(RefType.HISTORY, RefType.fromValue(2));
        assertEquals(RefType.META_INFO, RefType.fromValue(3));
    }

    @Test
    void fromValueInvalid() {
        assertThrows(IllegalArgumentException.class, () -> RefType.fromValue(99));
    }

    @Test
    void valueRoundTrip() {
        for (var type : RefType.values()) {
            assertEquals(type, RefType.fromValue(type.value()));
        }
    }

    @Test
    void ordinalMatchesValue() {
        assertEquals(0, RefType.CATALOG.value());
        assertEquals(1, RefType.CERTIFICATE.value());
        assertEquals(2, RefType.HISTORY.value());
        assertEquals(3, RefType.META_INFO.value());
    }
}
