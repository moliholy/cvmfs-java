package com.molina.cvmfs.directoryentry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FlagsTest {

    @Test
    void bitwiseAndOverlap() {
        assertEquals(4, 7 & Flags.FILE);
    }

    @Test
    void bitwiseAndNoOverlap() {
        assertEquals(0, Flags.DIRECTORY & Flags.FILE);
    }

    @Test
    void bitwiseAndSelf() {
        assertEquals(1, Flags.DIRECTORY & Flags.DIRECTORY);
    }

    @Test
    void contentHashTypeMask() {
        assertEquals(0x700, Flags.CONTENT_HASH_TYPE);
    }

    @Test
    void externalFileFlag() {
        assertEquals(128, Flags.EXTERNAL_FILE);
    }
}
