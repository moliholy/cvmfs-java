package com.molina.cvmfs.common;

import com.molina.cvmfs.directoryentry.Chunk;
import com.molina.cvmfs.directoryentry.ContentHashTypes;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChunkedFileAdditionalTest {

    @Test
    void findChunkIndexSingleChunk() {
        var chunks = List.of(new Chunk(0, 100, new byte[]{1}, ContentHashTypes.SHA1));
        var cf = new ChunkedFile(chunks, 100, null);
        assertEquals(0, cf.findChunkIndex(0));
        assertEquals(0, cf.findChunkIndex(50));
        assertEquals(0, cf.findChunkIndex(99));
    }

    @Test
    void findChunkIndexMultipleChunks() {
        var chunks = List.of(
                new Chunk(0, 100, new byte[]{1}, ContentHashTypes.SHA1),
                new Chunk(100, 200, new byte[]{2}, ContentHashTypes.SHA1),
                new Chunk(300, 100, new byte[]{3}, ContentHashTypes.SHA1));
        var cf = new ChunkedFile(chunks, 400, null);
        assertEquals(0, cf.findChunkIndex(0));
        assertEquals(0, cf.findChunkIndex(99));
        assertEquals(1, cf.findChunkIndex(100));
        assertEquals(1, cf.findChunkIndex(299));
        assertEquals(2, cf.findChunkIndex(300));
        assertEquals(2, cf.findChunkIndex(399));
    }

    @Test
    void findChunkIndexOutOfBounds() {
        var chunks = List.of(new Chunk(0, 100, new byte[]{1}, ContentHashTypes.SHA1));
        var cf = new ChunkedFile(chunks, 100, null);
        assertEquals(-1, cf.findChunkIndex(-1));
        assertEquals(-1, cf.findChunkIndex(100));
        assertEquals(-1, cf.findChunkIndex(200));
    }

    @Test
    void fileSizeReturnsCorrectSize() {
        var chunks = List.of(new Chunk(0, 500, new byte[]{1}, ContentHashTypes.SHA1));
        var cf = new ChunkedFile(chunks, 500, null);
        assertEquals(500, cf.fileSize());
    }

    @Test
    void chunksListIsImmutable() {
        var chunks = List.of(new Chunk(0, 100, new byte[]{1}, ContentHashTypes.SHA1));
        var cf = new ChunkedFile(chunks, 100, null);
        assertThrows(UnsupportedOperationException.class, () ->
                cf.chunks().add(new Chunk(100, 100, new byte[]{2}, ContentHashTypes.SHA1)));
    }

    @Test
    void closeDoesNotThrow() throws Exception {
        var chunks = List.of(new Chunk(0, 100, new byte[]{1}, ContentHashTypes.SHA1));
        var cf = new ChunkedFile(chunks, 100, null);
        cf.close();
    }

    @Test
    void prefetchAhead() {
        assertEquals(8, ChunkedFile.PREFETCH_AHEAD);
    }

    @Test
    void readAtEmptyBuffer() throws Exception {
        var chunks = List.of(new Chunk(0, 100, new byte[]{1}, ContentHashTypes.SHA1));
        var cf = new ChunkedFile(chunks, 100, null);
        assertEquals(0, cf.readAt(0, new byte[0]));
        cf.close();
    }

    @Test
    void readAtBeyondSize() throws Exception {
        var chunks = List.of(new Chunk(0, 100, new byte[]{1}, ContentHashTypes.SHA1));
        var cf = new ChunkedFile(chunks, 100, null);
        assertEquals(0, cf.readAt(100, new byte[10]));
        cf.close();
    }

    @Test
    void findChunkIndexEmptyChunks() {
        var cf = new ChunkedFile(List.of(), 0, null);
        assertEquals(-1, cf.findChunkIndex(0));
    }
}
