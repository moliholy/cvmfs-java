package com.molina.cvmfs.directoryentry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContentHashTypesTest {

    @Test
    void sha1SuffixEmpty() {
        assertEquals("", ContentHashTypes.SHA1.suffix());
    }

    @Test
    void ripemd160Suffix() {
        assertEquals("-rmd160", ContentHashTypes.RIPEMD160.suffix());
    }

    @Test
    void sha256Suffix() {
        assertEquals("-sha256", ContentHashTypes.SHA256.suffix());
    }

    @Test
    void shake128Suffix() {
        assertEquals("-shake128", ContentHashTypes.SHAKE128.suffix());
    }

    @Test
    void unknownSuffixEmpty() {
        assertEquals("", ContentHashTypes.UNKNOWN.suffix());
    }

    @Test
    void fromValue1IsSha1() {
        assertEquals(ContentHashTypes.SHA1, ContentHashTypes.fromValue(1));
    }

    @Test
    void fromValue2IsRipemd160() {
        assertEquals(ContentHashTypes.RIPEMD160, ContentHashTypes.fromValue(2));
    }

    @Test
    void fromValue3IsSha256() {
        assertEquals(ContentHashTypes.SHA256, ContentHashTypes.fromValue(3));
    }

    @Test
    void fromValue4IsShake128() {
        assertEquals(ContentHashTypes.SHAKE128, ContentHashTypes.fromValue(4));
    }

    @Test
    void fromValue0IsUnknown() {
        assertEquals(ContentHashTypes.UNKNOWN, ContentHashTypes.fromValue(0));
    }

    @Test
    void fromValue99IsUnknown() {
        assertEquals(ContentHashTypes.UNKNOWN, ContentHashTypes.fromValue(99));
    }
}
