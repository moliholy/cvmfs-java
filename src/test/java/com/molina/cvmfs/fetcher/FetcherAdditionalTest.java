package com.molina.cvmfs.fetcher;

import com.molina.cvmfs.common.CvmfsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.DeflaterOutputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class FetcherAdditionalTest {

    @TempDir
    Path tempDir;

    private Fetcher createLocalFetcher(Path sourceDir) throws Exception {
        Files.createDirectories(tempDir.resolve("cache/data"));
        return new Fetcher(sourceDir.toString(), tempDir.resolve("cache").toString());
    }

    @Test
    void retrieveRawFileFromLocalDir() throws Exception {
        var sourceDir = tempDir.resolve("source");
        Files.createDirectories(sourceDir);
        Files.writeString(sourceDir.resolve("testfile"), "content");

        var fetcher = createLocalFetcher(sourceDir);
        var path = fetcher.retrieveRawFile("testfile");
        assertEquals("content", Files.readString(path));
    }

    @Test
    void retrieveRawFileFailsWithAllSources() throws Exception {
        var sourceDir = tempDir.resolve("source");
        Files.createDirectories(sourceDir);
        var fetcher = createLocalFetcher(sourceDir);
        assertThrows(IOException.class, () -> fetcher.retrieveRawFile("nonexistent"));
    }

    @Test
    void cacheHitReturnsPath() throws Exception {
        var sourceDir = tempDir.resolve("source");
        Files.createDirectories(sourceDir);
        var cachePath = tempDir.resolve("cache");
        Files.createDirectories(cachePath.resolve("data/ab"));
        Files.writeString(cachePath.resolve("data/ab/cdef"), "cached");

        var fetcher = new Fetcher("http://invalid.local", cachePath.toString());
        var path = fetcher.retrieveFile("data/ab/cdef");
        assertEquals("cached", Files.readString(path));
    }

    @Test
    void retrieveFileCacheMiss() throws Exception {
        Files.createDirectories(tempDir.resolve("cache/data"));
        var fetcher = new Fetcher("http://invalid.local:1", tempDir.resolve("cache").toString());
        assertThrows(CvmfsException.class, () -> fetcher.retrieveFile("data/xx/missing"));
    }

    @Test
    void retrieveFileFromLocalDir() throws Exception {
        var sourceDir = tempDir.resolve("source");
        Files.createDirectories(sourceDir.resolve("data/ab"));

        var original = "test content for compression";
        var baos = new ByteArrayOutputStream();
        try (var out = new DeflaterOutputStream(baos)) {
            out.write(original.getBytes());
        }
        Files.write(sourceDir.resolve("data/ab/cdef"), baos.toByteArray());

        var fetcher = createLocalFetcher(sourceDir);
        var path = fetcher.retrieveFile("data/ab/cdef");
        assertEquals(original, Files.readString(path));
    }

    @Test
    void retrieveFileReturnsCached() throws Exception {
        var sourceDir = tempDir.resolve("source");
        Files.createDirectories(sourceDir.resolve("data/ab"));

        var original = "cached content";
        var baos = new ByteArrayOutputStream();
        try (var out = new DeflaterOutputStream(baos)) {
            out.write(original.getBytes());
        }
        Files.write(sourceDir.resolve("data/ab/cdef"), baos.toByteArray());

        var fetcher = createLocalFetcher(sourceDir);
        var first = fetcher.retrieveFile("data/ab/cdef");
        var second = fetcher.retrieveFile("data/ab/cdef");
        assertEquals(first, second);
    }

    @Test
    void retrieveFileVerifiedCorrectHash() throws Exception {
        var sourceDir = tempDir.resolve("source");
        Files.createDirectories(sourceDir.resolve("data/ab"));

        var original = "verify me";
        var baos = new ByteArrayOutputStream();
        try (var out = new DeflaterOutputStream(baos)) {
            out.write(original.getBytes());
        }
        Files.write(sourceDir.resolve("data/ab/cdef"), baos.toByteArray());

        var md = java.security.MessageDigest.getInstance("SHA-1");
        var hash = java.util.HexFormat.of().formatHex(md.digest(original.getBytes()));

        var fetcher = createLocalFetcher(sourceDir);
        var path = fetcher.retrieveFileVerified("data/ab/cdef", hash);
        assertEquals(original, Files.readString(path));
    }

    @Test
    void retrieveFileVerifiedBadHash() throws Exception {
        var sourceDir = tempDir.resolve("source");
        Files.createDirectories(sourceDir.resolve("data/ab"));

        var original = "bad hash test";
        var baos = new ByteArrayOutputStream();
        try (var out = new DeflaterOutputStream(baos)) {
            out.write(original.getBytes());
        }
        Files.write(sourceDir.resolve("data/ab/cdef"), baos.toByteArray());

        var fetcher = createLocalFetcher(sourceDir);
        assertThrows(CvmfsException.class, () ->
                fetcher.retrieveFileVerified("data/ab/cdef", "0".repeat(40)));
    }

    @Test
    void sortMirrorsByGeoNoOp() throws Exception {
        var sourceDir = tempDir.resolve("source");
        Files.createDirectories(sourceDir);
        var fetcher = createLocalFetcher(sourceDir);
        var originalSource = fetcher.source();
        fetcher.sortMirrorsByGeo("http://invalid.local:99999", "repo");
        assertNotNull(fetcher.source());
    }

    @Test
    void setSourcesEmpty() throws Exception {
        var sourceDir = tempDir.resolve("source");
        Files.createDirectories(sourceDir);
        var fetcher = createLocalFetcher(sourceDir);
        var original = fetcher.source();
        fetcher.setSources(List.of());
        assertEquals(original, fetcher.source());
    }

    @Test
    void constants() {
        assertEquals(3, Fetcher.MAX_RETRIES);
        assertTrue(Fetcher.REQUEST_TIMEOUT.getSeconds() > 0);
        assertTrue(Fetcher.MAX_DOWNLOAD_SIZE > 0);
        assertTrue(Fetcher.BACKOFF_INIT.compareTo(Fetcher.BACKOFF_MAX) < 0);
    }

    @Test
    void ioErrorCountIncrements() throws Exception {
        Files.createDirectories(tempDir.resolve("cache/data"));
        var fetcher = new Fetcher("http://invalid.local:1", tempDir.resolve("cache").toString());
        assertEquals(0, fetcher.ioErrorCount());
        try { fetcher.retrieveFile("data/xx/missing"); } catch (Exception ignored) {}
        assertTrue(fetcher.ioErrorCount() > 0);
    }
}
