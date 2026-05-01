package com.molina.cvmfs.catalog;

import com.molina.cvmfs.directoryentry.DirectoryEntry;
import com.molina.cvmfs.directoryentry.DirectoryEntryWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class CatalogTest {

    @TempDir
    Path tempDir;
    private File dbFile;
    private Connection conn;

    @BeforeEach
    void setUp() throws Exception {
        dbFile = tempDir.resolve("catalog.db").toFile();
        conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        conn.setAutoCommit(true);
        conn.createStatement().executeUpdate(
                "CREATE TABLE properties (key TEXT, value TEXT)");
        conn.createStatement().executeUpdate(
                "CREATE TABLE catalog (md5path_1 INTEGER, md5path_2 INTEGER, " +
                        "parent_1 INTEGER, parent_2 INTEGER, hash BLOB, flags INTEGER, " +
                        "size INTEGER, mode INTEGER, mtime INTEGER, name TEXT, symlink TEXT)");
        conn.createStatement().executeUpdate(
                "CREATE TABLE nested_catalogs (path TEXT, sha1 TEXT)");
        conn.createStatement().executeUpdate(
                "CREATE TABLE chunks (md5path_1 INTEGER, md5path_2 INTEGER, " +
                        "offset INTEGER, size INTEGER, hash BLOB)");

        conn.createStatement().executeUpdate(
                "INSERT INTO properties VALUES ('schema', '2.5')");
        conn.createStatement().executeUpdate(
                "INSERT INTO properties VALUES ('schema_revision', '0')");
        conn.createStatement().executeUpdate(
                "INSERT INTO properties VALUES ('revision', '42')");
        conn.createStatement().executeUpdate(
                "INSERT INTO properties VALUES ('last_modified', '1700000000')");
        conn.createStatement().executeUpdate(
                "INSERT INTO properties VALUES ('root_prefix', '/')");
        conn.createStatement().executeUpdate(
                "INSERT INTO properties VALUES ('previous_revision', 'abc123')");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (conn != null) conn.close();
    }

    private Catalog openCatalog() throws SQLException {
        return new Catalog(dbFile, "testhash");
    }

    @Test
    void readsCatalogProperties() throws SQLException {
        try (var catalog = openCatalog()) {
            assertEquals(2.5f, catalog.schema(), 0.01);
            assertEquals(0f, catalog.schemaRevision(), 0.01);
            assertEquals(42, catalog.revision());
            assertEquals("testhash", catalog.hash());
            assertEquals(1700000000L, catalog.lastModified());
            assertEquals("/", catalog.rootPrefix());
            assertEquals("abc123", catalog.previousRevision());
        }
    }

    @Test
    void isRoot() throws SQLException {
        try (var catalog = openCatalog()) {
            assertTrue(catalog.isRoot());
        }
    }

    @Test
    void isNotRoot() throws Exception {
        conn.createStatement().executeUpdate("DELETE FROM properties WHERE key = 'root_prefix'");
        conn.createStatement().executeUpdate(
                "INSERT INTO properties VALUES ('root_prefix', '/nested/path')");
        try (var catalog = new Catalog(dbFile, "h")) {
            assertFalse(catalog.isRoot());
            assertEquals("/nested/path", catalog.rootPrefix());
        }
    }

    @Test
    void hasNestedFalseWhenEmpty() throws SQLException {
        try (var catalog = openCatalog()) {
            assertFalse(catalog.hasNested());
            assertEquals(0, catalog.nestedCount());
        }
    }

    @Test
    void hasNestedTrueWhenPresent() throws Exception {
        conn.createStatement().executeUpdate(
                "INSERT INTO nested_catalogs VALUES ('/nested', 'abc123')");
        try (var catalog = openCatalog()) {
            assertTrue(catalog.hasNested());
            assertEquals(1, catalog.nestedCount());
        }
    }

    @Test
    void listNested() throws Exception {
        conn.createStatement().executeUpdate(
                "INSERT INTO nested_catalogs VALUES ('/a', 'hash_a')");
        conn.createStatement().executeUpdate(
                "INSERT INTO nested_catalogs VALUES ('/b', 'hash_b')");
        try (var catalog = openCatalog()) {
            var nested = catalog.listNested();
            assertEquals(2, nested.size());
            assertEquals("/a", nested.get(0).rootPath());
            assertEquals("hash_a", nested.get(0).catalogHash());
            assertEquals("/b", nested.get(1).rootPath());
            assertEquals("hash_b", nested.get(1).catalogHash());
        }
    }

    @Test
    void findNestedForPathBestMatch() throws Exception {
        conn.createStatement().executeUpdate(
                "INSERT INTO nested_catalogs VALUES ('/software', 'hash1')");
        conn.createStatement().executeUpdate(
                "INSERT INTO nested_catalogs VALUES ('/software/releases', 'hash2')");
        try (var catalog = openCatalog()) {
            var result = catalog.findNestedForPath("/software/releases/v1");
            assertTrue(result.isPresent());
            assertEquals("hash2", result.get().catalogHash());
        }
    }

    @Test
    void findNestedForPathNoMatch() throws Exception {
        conn.createStatement().executeUpdate(
                "INSERT INTO nested_catalogs VALUES ('/software', 'hash1')");
        try (var catalog = openCatalog()) {
            var result = catalog.findNestedForPath("/other/path");
            assertTrue(result.isEmpty());
        }
    }

    @Test
    void findNestedForPathSanitization() throws Exception {
        conn.createStatement().executeUpdate(
                "INSERT INTO nested_catalogs VALUES ('/soft', 'hash1')");
        try (var catalog = openCatalog()) {
            var result = catalog.findNestedForPath("/software/releases");
            assertTrue(result.isEmpty());
        }
    }

    @Test
    void listDirectoryEmpty() throws SQLException {
        try (var catalog = openCatalog()) {
            var entries = catalog.listDirectory("/nonexistent");
            assertTrue(entries.isEmpty());
        }
    }

    @Test
    void listDirectoryWithEntries() throws Exception {
        var md = java.security.MessageDigest.getInstance("MD5");
        var hash = com.molina.cvmfs.common.Common.splitMd5(
                md.digest("".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        conn.createStatement().executeUpdate(String.format(
                "INSERT INTO catalog VALUES (100, 200, %d, %d, X'aabb', %d, 1024, 33188, 1700000000, 'file.txt', NULL)",
                hash.hash1(), hash.hash2(), 4));
        try (var catalog = openCatalog()) {
            var entries = catalog.listDirectory("/");
            assertEquals(1, entries.size());
            assertEquals("file.txt", entries.getFirst().name());
        }
    }

    @Test
    void findDirectoryEntryPresent() throws Exception {
        var md = java.security.MessageDigest.getInstance("MD5");
        var pathHash = com.molina.cvmfs.common.Common.splitMd5(
                md.digest("test".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        conn.createStatement().executeUpdate(String.format(
                "INSERT INTO catalog VALUES (%d, %d, 0, 0, X'aabb', %d, 512, 33188, 1700000000, 'test', NULL)",
                pathHash.hash1(), pathHash.hash2(), 4));
        try (var catalog = openCatalog()) {
            var entry = catalog.findDirectoryEntry("test");
            assertTrue(entry.isPresent());
            assertEquals("test", entry.get().name());
        }
    }

    @Test
    void findDirectoryEntryMissing() throws SQLException {
        try (var catalog = openCatalog()) {
            var entry = catalog.findDirectoryEntry("missing");
            assertTrue(entry.isEmpty());
        }
    }

    @Test
    void getStatistics() throws Exception {
        conn.createStatement().executeUpdate(
                "CREATE TABLE statistics (counter TEXT, value INTEGER)");
        conn.createStatement().executeUpdate(
                "INSERT INTO statistics VALUES ('self_regular', 100)");
        conn.createStatement().executeUpdate(
                "INSERT INTO statistics VALUES ('self_dir', 10)");
        conn.createStatement().executeUpdate(
                "INSERT INTO statistics VALUES ('subtree_regular', 200)");
        try (var catalog = openCatalog()) {
            var stats = catalog.getStatistics();
            assertEquals(100, stats.regular());
            assertEquals(10, stats.dir());
            assertEquals(300, stats.stat("all_regular"));
        }
    }

    @Test
    void iteratorTraversesCatalog() throws Exception {
        var md = java.security.MessageDigest.getInstance("MD5");
        var rootHash = com.molina.cvmfs.common.Common.splitMd5(
                md.digest("".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        conn.createStatement().executeUpdate(String.format(
                "INSERT INTO catalog VALUES (%d, %d, 0, 0, X'aabb', %d, 0, 16877, 1700000000, '', NULL)",
                rootHash.hash1(), rootHash.hash2(), 1));
        conn.createStatement().executeUpdate(String.format(
                "INSERT INTO catalog VALUES (10, 20, %d, %d, X'ccdd', %d, 100, 33188, 1700000000, 'child.txt', NULL)",
                rootHash.hash1(), rootHash.hash2(), 4));

        try (var catalog = openCatalog()) {
            var entries = new ArrayList<DirectoryEntryWrapper>();
            for (var wrapper : catalog) {
                entries.add(wrapper);
            }
            assertFalse(entries.isEmpty());
        }
    }

    @Test
    void closeIsIdempotent() throws SQLException {
        var catalog = openCatalog();
        catalog.close();
        catalog.close();
    }

    @Test
    void defaultRootPrefixWhenMissing() throws Exception {
        conn.createStatement().executeUpdate("DELETE FROM properties WHERE key = 'root_prefix'");
        try (var catalog = new Catalog(dbFile, "h")) {
            assertEquals("/", catalog.rootPrefix());
        }
    }
}
