package com.molina.cvmfs.catalog;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.*;

class CatalogStatisticsTest {

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
                "CREATE TABLE statistics (counter TEXT, value INTEGER)");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (conn != null) conn.close();
    }

    @Test
    void readsSelfCounters() throws Exception {
        conn.createStatement().executeUpdate(
                "INSERT INTO properties VALUES ('schema', '2.5')");
        conn.createStatement().executeUpdate(
                "INSERT INTO statistics VALUES ('self_regular', 50)");
        conn.createStatement().executeUpdate(
                "INSERT INTO statistics VALUES ('self_dir', 10)");
        conn.createStatement().executeUpdate(
                "INSERT INTO statistics VALUES ('self_symlink', 5)");
        conn.createStatement().executeUpdate(
                "INSERT INTO statistics VALUES ('self_file_size', 999)");
        conn.createStatement().executeUpdate(
                "INSERT INTO statistics VALUES ('self_chunked', 3)");
        conn.createStatement().executeUpdate(
                "INSERT INTO statistics VALUES ('self_chunks', 12)");
        try (var catalog = new Catalog(dbFile, "h")) {
            var stats = catalog.getStatistics();
            assertEquals(50, stats.regular());
            assertEquals(10, stats.dir());
            assertEquals(5, stats.symlink());
            assertEquals(999, stats.fileSize());
            assertEquals(3, stats.chunked());
            assertEquals(12, stats.chunks());
        }
    }

    @Test
    void subtreeAggregatesWithSelf() throws Exception {
        conn.createStatement().executeUpdate(
                "INSERT INTO properties VALUES ('schema', '2.5')");
        conn.createStatement().executeUpdate(
                "INSERT INTO statistics VALUES ('self_regular', 50)");
        conn.createStatement().executeUpdate(
                "INSERT INTO statistics VALUES ('subtree_regular', 200)");
        try (var catalog = new Catalog(dbFile, "h")) {
            var stats = catalog.getStatistics();
            assertEquals(50, stats.regular());
            assertEquals(250, stats.stat("all_regular"));
        }
    }

    @Test
    void unknownCounterDefaultsToZero() throws Exception {
        conn.createStatement().executeUpdate(
                "INSERT INTO properties VALUES ('schema', '2.5')");
        try (var catalog = new Catalog(dbFile, "h")) {
            var stats = catalog.getStatistics();
            assertEquals(0, stats.stat("nonexistent"));
            assertEquals(0, stats.regular());
        }
    }

    @Test
    void skipsStatisticsForOldSchema() throws Exception {
        conn.createStatement().executeUpdate(
                "INSERT INTO properties VALUES ('schema', '1.0')");
        conn.createStatement().executeUpdate(
                "INSERT INTO statistics VALUES ('self_regular', 50)");
        try (var catalog = new Catalog(dbFile, "h")) {
            var stats = catalog.getStatistics();
            assertEquals(0, stats.regular());
        }
    }
}
