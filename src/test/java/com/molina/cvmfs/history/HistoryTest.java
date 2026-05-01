package com.molina.cvmfs.history;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.*;

class HistoryTest {

    @TempDir
    Path tempDir;
    private File dbFile;
    private Connection conn;

    @BeforeEach
    void setUp() throws Exception {
        dbFile = tempDir.resolve("history.db").toFile();
        conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        conn.setAutoCommit(true);
        conn.createStatement().executeUpdate(
                "CREATE TABLE properties (key TEXT, value TEXT)");
        conn.createStatement().executeUpdate(
                "INSERT INTO properties VALUES ('schema', '1.0')");
        conn.createStatement().executeUpdate(
                "INSERT INTO properties VALUES ('fqrn', 'atlas.cern.ch')");
        conn.createStatement().executeUpdate(
                "CREATE TABLE tags (name TEXT, hash TEXT, revision INTEGER, " +
                        "timestamp INTEGER, channel INTEGER, description TEXT)");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (conn != null) conn.close();
    }

    @Test
    void readsProperties() throws Exception {
        try (var history = new History(dbFile)) {
            assertEquals("1.0", history.schema());
            assertEquals("atlas.cern.ch", history.fqrn());
        }
    }

    @Test
    void listTagsEmpty() throws Exception {
        try (var history = new History(dbFile)) {
            assertTrue(history.listTags().isEmpty());
        }
    }

    @Test
    void listTagsOrderedByTimestampDesc() throws Exception {
        conn.createStatement().executeUpdate(
                "INSERT INTO tags VALUES ('v1', 'hash1', 1, 1000, 0, 'first')");
        conn.createStatement().executeUpdate(
                "INSERT INTO tags VALUES ('v2', 'hash2', 2, 2000, 0, 'second')");
        conn.createStatement().executeUpdate(
                "INSERT INTO tags VALUES ('v3', 'hash3', 3, 3000, 0, 'third')");
        try (var history = new History(dbFile)) {
            var tags = history.listTags();
            assertEquals(3, tags.size());
            assertEquals("v3", tags.get(0).name());
            assertEquals("v1", tags.get(2).name());
        }
    }

    @Test
    void getTagByName() throws Exception {
        conn.createStatement().executeUpdate(
                "INSERT INTO tags VALUES ('release-1', 'abc123', 5, 1000, 1, 'Release 1')");
        try (var history = new History(dbFile)) {
            var tag = history.getTagByName("release-1");
            assertTrue(tag.isPresent());
            assertEquals("release-1", tag.get().name());
            assertEquals("abc123", tag.get().hash());
            assertEquals(5, tag.get().revision());
            assertEquals(1000, tag.get().timestamp());
            assertEquals(1, tag.get().channel());
            assertEquals("Release 1", tag.get().description());
        }
    }

    @Test
    void getTagByNameMissing() throws Exception {
        try (var history = new History(dbFile)) {
            assertTrue(history.getTagByName("nonexistent").isEmpty());
        }
    }

    @Test
    void getTagByRevision() throws Exception {
        conn.createStatement().executeUpdate(
                "INSERT INTO tags VALUES ('v1', 'hash1', 10, 1000, 0, 'desc')");
        try (var history = new History(dbFile)) {
            var tag = history.getTagByRevision(10);
            assertTrue(tag.isPresent());
            assertEquals("v1", tag.get().name());
        }
    }

    @Test
    void getTagByRevisionMissing() throws Exception {
        try (var history = new History(dbFile)) {
            assertTrue(history.getTagByRevision(999).isEmpty());
        }
    }

    @Test
    void getTagByDate() throws Exception {
        conn.createStatement().executeUpdate(
                "INSERT INTO tags VALUES ('v1', 'h1', 1, 1000, 0, 'd')");
        conn.createStatement().executeUpdate(
                "INSERT INTO tags VALUES ('v2', 'h2', 2, 2000, 0, 'd')");
        try (var history = new History(dbFile)) {
            var tag = history.getTagByDate(500);
            assertTrue(tag.isPresent());
            assertEquals("v1", tag.get().name());
        }
    }

    @Test
    void getTagByDateNoMatch() throws Exception {
        conn.createStatement().executeUpdate(
                "INSERT INTO tags VALUES ('v1', 'h1', 1, 1000, 0, 'd')");
        try (var history = new History(dbFile)) {
            assertTrue(history.getTagByDate(2000).isEmpty());
        }
    }

    @Test
    void closeIsIdempotent() throws Exception {
        var history = new History(dbFile);
        history.close();
        history.close();
    }
}
