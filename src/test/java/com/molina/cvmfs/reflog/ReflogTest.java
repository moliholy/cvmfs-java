package com.molina.cvmfs.reflog;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.*;

class ReflogTest {

    @TempDir
    Path tempDir;
    File dbFile;
    Reflog reflog;

    @BeforeEach
    void setUp() throws Exception {
        dbFile = tempDir.resolve("reflog.db").toFile();
        try (var conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath())) {
            conn.createStatement().execute("""
                    CREATE TABLE refs (
                        hash TEXT NOT NULL,
                        type INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL
                    )""");
            conn.createStatement().execute(
                    "INSERT INTO refs VALUES ('abc123', 0, 1000)");
            conn.createStatement().execute(
                    "INSERT INTO refs VALUES ('def456', 1, 2000)");
            conn.createStatement().execute(
                    "INSERT INTO refs VALUES ('ghi789', 0, 3000)");
            conn.createStatement().execute(
                    "INSERT INTO refs VALUES ('jkl012', 2, 4000)");
        }
        reflog = new Reflog(dbFile);
    }

    @AfterEach
    void tearDown() {
        reflog.close();
    }

    @Test
    void listRefs() throws Exception {
        var refs = reflog.listRefs();
        assertEquals(4, refs.size());
        assertEquals("jkl012", refs.getFirst().hash());
        assertEquals("abc123", refs.getLast().hash());
    }

    @Test
    void listRefsByType() throws Exception {
        var catalogs = reflog.listRefsByType(RefType.CATALOG);
        assertEquals(2, catalogs.size());
        assertTrue(catalogs.stream().allMatch(r -> r.refType() == RefType.CATALOG));

        var certs = reflog.listRefsByType(RefType.CERTIFICATE);
        assertEquals(1, certs.size());
        assertEquals("def456", certs.getFirst().hash());
    }

    @Test
    void countRefs() throws Exception {
        assertEquals(4, reflog.countRefs());
    }

    @Test
    void containsHash() throws Exception {
        assertTrue(reflog.containsHash("abc123"));
        assertTrue(reflog.containsHash("def456"));
        assertFalse(reflog.containsHash("nonexistent"));
    }

    @Test
    void emptyReflog() throws Exception {
        var emptyFile = tempDir.resolve("empty.db").toFile();
        try (var conn = DriverManager.getConnection("jdbc:sqlite:" + emptyFile.getAbsolutePath())) {
            conn.createStatement().execute("""
                    CREATE TABLE refs (
                        hash TEXT NOT NULL,
                        type INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL
                    )""");
        }
        try (var emptyReflog = new Reflog(emptyFile)) {
            assertTrue(emptyReflog.listRefs().isEmpty());
            assertEquals(0, emptyReflog.countRefs());
            assertFalse(emptyReflog.containsHash("anything"));
        }
    }

    @Test
    void refEntryRecord() {
        var entry = new RefEntry("hash123", RefType.CATALOG, 12345);
        assertEquals("hash123", entry.hash());
        assertEquals(RefType.CATALOG, entry.refType());
        assertEquals(12345, entry.timestamp());
    }
}
