package com.molina.cvmfs.directoryentry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChunkTest {

    @Test
    void contentHashStringSha1() {
        var chunk = new Chunk(0, 100, new byte[]{(byte) 0xAB, (byte) 0xC1, 0x23}, ContentHashTypes.SHA1);
        assertEquals("abc123", chunk.contentHashString());
    }

    @Test
    void contentHashStringRipemd160() {
        var chunk = new Chunk(0, 100, new byte[]{(byte) 0xDE, (byte) 0xAD}, ContentHashTypes.RIPEMD160);
        assertEquals("dead-rmd160", chunk.contentHashString());
    }

    @Test
    void accessors() {
        var chunk = new Chunk(10, 200, new byte[]{0x01}, ContentHashTypes.SHA256);
        assertEquals(10, chunk.offset());
        assertEquals(200, chunk.size());
        assertEquals("01", chunk.contentHash());
        assertEquals(ContentHashTypes.SHA256, chunk.contentHashType());
    }
}
