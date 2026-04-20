package com.molina.cvmfs.fetcher;

import com.molina.cvmfs.common.CvmfsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class CacheTest {

    @TempDir
    Path tempDir;
    Cache cache;

    @BeforeEach
    void setUp() throws Exception {
        cache = new Cache(tempDir.toString());
    }

    @Test
    void createsDataStructure() {
        assertTrue(Files.isDirectory(tempDir.resolve("data")));
        assertTrue(Files.isDirectory(tempDir.resolve("data/00")));
        assertTrue(Files.isDirectory(tempDir.resolve("data/ff")));
    }

    @Test
    void addReturnsPath() {
        var path = cache.add("data/ab/cdef1234");
        assertEquals(tempDir.resolve("data/ab/cdef1234"), path);
    }

    @Test
    void addRejectsTraversal() {
        assertThrows(IllegalArgumentException.class, () -> cache.add("../etc/passwd"));
        assertThrows(IllegalArgumentException.class, () -> cache.add("/etc/passwd"));
    }

    @Test
    void getReturnsNullForMissing() {
        assertNull(cache.get("nonexistent"));
    }

    @Test
    void getReturnsExistingFile() throws IOException {
        Files.writeString(tempDir.resolve("testfile"), "data");
        assertNotNull(cache.get("testfile"));
    }

    @Test
    void dataFilesNeverExpire() throws Exception {
        cache.withTtl(Duration.ZERO, Duration.ofSeconds(5));
        var path = cache.add("data/ab/testfile");
        Files.writeString(path, "content");
        assertNotNull(cache.get("data/ab/testfile"));
    }

    @Test
    void metadataFilesExpire() throws Exception {
        cache.withTtl(Duration.ZERO, Duration.ofSeconds(5));
        Files.writeString(tempDir.resolve("metadata"), "data");
        assertNull(cache.get("metadata"));
    }

    @Test
    void negativeCaching() {
        assertNull(cache.get("missing"));
        cache.recordNegative("missing");
        assertNull(cache.get("missing"));
        assertTrue(cache.isNegativeCached("missing"));
    }

    @Test
    void negativeCacheExpires() throws Exception {
        cache.withTtl(Duration.ofHours(1), Duration.ZERO);
        cache.recordNegative("missing");
        assertFalse(cache.isNegativeCached("missing"));
    }

    @Test
    void cacheSize() throws IOException {
        var path = cache.add("data/ab/file1");
        Files.writeString(path, "hello");
        assertTrue(cache.cacheSize() > 0);
    }

    @Test
    void enforceQuota() throws IOException {
        var path = cache.add("data/ab/bigfile");
        Files.writeString(path, "x".repeat(1000));
        cache.withQuota(100);
        cache.enforceQuota();
        assertFalse(Files.exists(path));
    }

    @Test
    void evictClearsNegativeEntries() throws IOException {
        cache.recordNegative("missing");
        cache.evict();
        assertFalse(cache.isNegativeCached("missing"));
    }

    @Test
    void defaultValues() {
        assertEquals(Duration.ofHours(1), cache.ttl());
        assertEquals(Duration.ofSeconds(5), cache.negativeTtl());
        assertEquals(4L * 1024 * 1024 * 1024, cache.quota());
    }

    @Test
    void builderChaining() {
        cache.withTtl(Duration.ofMinutes(30), Duration.ofSeconds(10)).withQuota(1024);
        assertEquals(Duration.ofMinutes(30), cache.ttl());
        assertEquals(Duration.ofSeconds(10), cache.negativeTtl());
        assertEquals(1024, cache.quota());
    }

    @Test
    void invalidCacheDir() {
        assertThrows(CvmfsException.class, () -> new Cache("/nonexistent/path"));
    }
}
