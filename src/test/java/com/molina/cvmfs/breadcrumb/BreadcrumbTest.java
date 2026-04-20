package com.molina.cvmfs.breadcrumb;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BreadcrumbTest {

    @TempDir
    Path tempDir;

    @Test
    void writeAndRead() throws Exception {
        Breadcrumb.write(tempDir.toString(), "repo.cern.ch", "abc123");
        var result = Breadcrumb.read(tempDir.toString(), "repo.cern.ch");
        assertTrue(result.isPresent());
        assertEquals("abc123", result.get());
    }

    @Test
    void readNonexistent() {
        var result = Breadcrumb.read(tempDir.toString(), "nonexistent.repo");
        assertTrue(result.isEmpty());
    }

    @Test
    void remove() throws Exception {
        Breadcrumb.write(tempDir.toString(), "repo.cern.ch", "abc123");
        assertTrue(Breadcrumb.read(tempDir.toString(), "repo.cern.ch").isPresent());
        Breadcrumb.remove(tempDir.toString(), "repo.cern.ch");
        assertTrue(Breadcrumb.read(tempDir.toString(), "repo.cern.ch").isEmpty());
    }

    @Test
    void removeNonexistentDoesNotThrow() {
        assertDoesNotThrow(() -> Breadcrumb.remove(tempDir.toString(), "nope"));
    }

    @Test
    void overwrite() throws Exception {
        Breadcrumb.write(tempDir.toString(), "repo.cern.ch", "hash1");
        Breadcrumb.write(tempDir.toString(), "repo.cern.ch", "hash2");
        assertEquals("hash2", Breadcrumb.read(tempDir.toString(), "repo.cern.ch").orElseThrow());
    }

    @Test
    void filePath() {
        var path = Breadcrumb.path("/cache", "atlas.cern.ch");
        assertEquals(Path.of("/cache/cvmfschecksum.atlas.cern.ch"), path);
    }

    @Test
    void fileCreatedOnDisk() throws Exception {
        Breadcrumb.write(tempDir.toString(), "test.repo", "deadbeef");
        var file = tempDir.resolve("cvmfschecksum.test.repo");
        assertTrue(Files.exists(file));
        assertEquals("deadbeef", Files.readString(file));
    }
}
