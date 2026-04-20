package com.molina.cvmfs.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RegularFileTest {

    @TempDir
    Path tempDir;

    @Test
    void readAtOffset() throws Exception {
        var path = tempDir.resolve("test.bin");
        Files.writeString(path, "hello world");
        try (var file = new RegularFile(path)) {
            assertEquals(11, file.fileSize());
            var buf = new byte[5];
            var read = file.readAt(6, buf);
            assertEquals(5, read);
            assertEquals("world", new String(buf));
        }
    }

    @Test
    void readFromStart() throws Exception {
        var path = tempDir.resolve("test.bin");
        Files.writeString(path, "abcdef");
        try (var file = new RegularFile(path)) {
            var buf = new byte[3];
            file.readAt(0, buf);
            assertEquals("abc", new String(buf));
        }
    }

    @Test
    void fileSize() throws Exception {
        var path = tempDir.resolve("test.bin");
        Files.write(path, new byte[100]);
        try (var file = new RegularFile(path)) {
            assertEquals(100, file.fileSize());
        }
    }
}
