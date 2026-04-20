package com.molina.cvmfs.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PathHashTest {

    @Test
    void recordAccessors() {
        var ph = new PathHash(42L, 99L);
        assertEquals(42L, ph.hash1());
        assertEquals(99L, ph.hash2());
    }

    @Test
    void recordEquality() {
        var a = new PathHash(1L, 2L);
        var b = new PathHash(1L, 2L);
        assertEquals(a, b);
    }

    @Test
    void recordInequality() {
        var a = new PathHash(1L, 2L);
        var b = new PathHash(3L, 4L);
        assertNotEquals(a, b);
    }
}
