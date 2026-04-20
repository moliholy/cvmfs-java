package com.molina.cvmfs.common;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CommonTest {

    @Test
    void splitMd5AllZeros() {
        var digest = new byte[16];
        var result = Common.splitMd5(digest);
        assertEquals(0, result.hash1());
        assertEquals(0, result.hash2());
    }

    @Test
    void splitMd5AllFF() {
        var digest = new byte[16];
        java.util.Arrays.fill(digest, (byte) 0xFF);
        var result = Common.splitMd5(digest);
        assertEquals(-1, result.hash1());
        assertEquals(-1, result.hash2());
    }

    @Test
    void splitMd5KnownValue() {
        var digest = new byte[16];
        digest[0] = 1;
        digest[8] = 2;
        var result = Common.splitMd5(digest);
        assertEquals(1, result.hash1());
        assertEquals(2, result.hash2());
    }

    @Test
    void splitMd5Asymmetric() {
        var digest = new byte[]{
                0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,
                0x10, 0x20, 0x30, 0x40, 0x50, 0x60, 0x70, (byte) 0x80
        };
        var result = Common.splitMd5(digest);
        long expectedLo = Long.reverseBytes(0x0102030405060708L);
        long expectedHi = Long.reverseBytes(0x1020304050607080L);
        assertEquals(expectedLo, result.hash1());
        assertEquals(expectedHi, result.hash2());
    }

    @Test
    void composeObjectPathNoSuffix() {
        assertEquals("data/ab/cdef1234", Common.composeObjectPath("abcdef1234", ""));
    }

    @Test
    void composeObjectPathWithSuffix() {
        assertEquals("data/ab/cdef1234C", Common.composeObjectPath("abcdef1234", "C"));
    }

    @Test
    void composeObjectPathRmd160Suffix() {
        assertEquals("data/de/adbeef00-rmd160", Common.composeObjectPath("deadbeef00", "-rmd160"));
    }

    @Test
    void toHexWorks() {
        assertEquals("0102ff", Common.toHex(new byte[]{0x01, 0x02, (byte) 0xFF}));
    }

    @Test
    void toHexEmpty() {
        assertEquals("", Common.toHex(new byte[0]));
    }

    @Test
    void md5HexProducesValidHash() {
        var hash = Common.md5Hex("test");
        assertNotNull(hash);
        assertEquals(32, hash.length());
    }

    @Test
    void canonicalizePathNormalizesTrailingSlash() {
        var result = Common.canonicalizePath("/foo/bar/");
        assertTrue(result.startsWith("/foo/bar"));
    }

    @Test
    void canonicalizePathEmpty() {
        assertEquals("", Common.canonicalizePath(""));
    }

    @Test
    void canonicalizePathNull() {
        assertEquals("", Common.canonicalizePath(null));
    }

    @Test
    void canonicalizePathRoot() {
        assertEquals("/", Common.canonicalizePath("/"));
    }
}
