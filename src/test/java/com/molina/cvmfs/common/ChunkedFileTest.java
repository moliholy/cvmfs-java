package com.molina.cvmfs.common;

import com.molina.cvmfs.directoryentry.Chunk;
import com.molina.cvmfs.directoryentry.ContentHashTypes;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChunkedFileTest {

    private List<Chunk> makeChunks() {
        return List.of(
                new Chunk(0, 100, new byte[]{0x01, 0x02}, ContentHashTypes.SHA1),
                new Chunk(100, 200, new byte[]{0x03, 0x04}, ContentHashTypes.SHA1),
                new Chunk(300, 150, new byte[]{0x05, 0x06}, ContentHashTypes.SHA1)
        );
    }

    @Test
    void findChunkIndexFirst() {
        var file = new ChunkedFile(makeChunks(), 450, null);
        assertEquals(0, file.findChunkIndex(0));
        assertEquals(0, file.findChunkIndex(50));
        assertEquals(0, file.findChunkIndex(99));
    }

    @Test
    void findChunkIndexSecond() {
        var file = new ChunkedFile(makeChunks(), 450, null);
        assertEquals(1, file.findChunkIndex(100));
        assertEquals(1, file.findChunkIndex(200));
        assertEquals(1, file.findChunkIndex(299));
    }

    @Test
    void findChunkIndexLast() {
        var file = new ChunkedFile(makeChunks(), 450, null);
        assertEquals(2, file.findChunkIndex(300));
        assertEquals(2, file.findChunkIndex(449));
    }

    @Test
    void findChunkIndexOutOfBounds() {
        var file = new ChunkedFile(makeChunks(), 450, null);
        assertEquals(-1, file.findChunkIndex(-1));
        assertEquals(-1, file.findChunkIndex(450));
        assertEquals(-1, file.findChunkIndex(1000));
    }

    @Test
    void fileSize() {
        var file = new ChunkedFile(makeChunks(), 450, null);
        assertEquals(450, file.fileSize());
    }

    @Test
    void chunksImmutable() {
        var file = new ChunkedFile(makeChunks(), 450, null);
        assertThrows(UnsupportedOperationException.class, () ->
                file.chunks().add(new Chunk(0, 0, new byte[0], ContentHashTypes.SHA1)));
    }

    @Test
    void readAtEmptyBuffer() throws Exception {
        var file = new ChunkedFile(makeChunks(), 450, null);
        assertEquals(0, file.readAt(0, new byte[0]));
    }

    @Test
    void readAtPastEnd() throws Exception {
        var file = new ChunkedFile(makeChunks(), 450, null);
        assertEquals(0, file.readAt(500, new byte[10]));
    }
}
