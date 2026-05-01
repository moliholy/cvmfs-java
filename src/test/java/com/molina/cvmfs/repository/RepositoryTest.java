package com.molina.cvmfs.repository;

import com.molina.cvmfs.common.CvmfsException;
import com.molina.cvmfs.fetcher.Fetcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.zip.DeflaterOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class RepositoryTest {

    @TempDir
    Path tempDir;
    private Path sourceDir;
    private Path cacheDir;

    @BeforeEach
    void setUp() throws Exception {
        sourceDir = tempDir.resolve("repo");
        cacheDir = tempDir.resolve("cache");
        Files.createDirectories(sourceDir);
        Files.createDirectories(cacheDir.resolve("data"));
    }

    private void writeManifest(String catalogHash) throws IOException {
        var manifest = "C" + catalogHash + "\n" +
                "Rroothash123\n" +
                "B1024\n" +
                "T1700000000\n" +
                "D900\n" +
                "S1\n" +
                "Ntest.example.com\n";
        Files.writeString(sourceDir.resolve(".cvmfspublished"), manifest);
    }

    private void writeManifestWithHistory(String catalogHash, String historyHash) throws IOException {
        var manifest = "C" + catalogHash + "\n" +
                "Rroothash123\n" +
                "B1024\n" +
                "T1700000000\n" +
                "D900\n" +
                "S1\n" +
                "Ntest.example.com\n" +
                "H" + historyHash + "\n";
        Files.writeString(sourceDir.resolve(".cvmfspublished"), manifest);
    }

    private void storeCompressedDb(Path dbFile, String hash, String suffix) throws IOException {
        var prefix = hash.substring(0, 2);
        var rest = hash.substring(2);
        var dir = sourceDir.resolve("data/" + prefix);
        Files.createDirectories(dir);
        Files.write(dir.resolve(rest + suffix), compress(Files.readAllBytes(dbFile)));
    }

    private void createCatalogDb(String hash) throws Exception {
        var dbTmp = tempDir.resolve("catalog_tmp_" + hash + ".db");
        var conn = DriverManager.getConnection("jdbc:sqlite:" + dbTmp);
        conn.setAutoCommit(true);
        conn.createStatement().executeUpdate(
                "CREATE TABLE properties (key TEXT, value TEXT)");
        conn.createStatement().executeUpdate(
                "INSERT INTO properties VALUES ('schema', '2.5')");
        conn.createStatement().executeUpdate(
                "INSERT INTO properties VALUES ('revision', '1')");
        conn.createStatement().executeUpdate(
                "INSERT INTO properties VALUES ('root_prefix', '/')");
        conn.createStatement().executeUpdate(
                "CREATE TABLE catalog (md5path_1 INTEGER, md5path_2 INTEGER, " +
                        "parent_1 INTEGER, parent_2 INTEGER, hash BLOB, flags INTEGER, " +
                        "size INTEGER, mode INTEGER, mtime INTEGER, name TEXT, symlink TEXT)");

        var md = java.security.MessageDigest.getInstance("MD5");
        var rootHash = com.molina.cvmfs.common.Common.splitMd5(
                md.digest("".getBytes(StandardCharsets.UTF_8)));
        conn.createStatement().executeUpdate(String.format(
                "INSERT INTO catalog VALUES (%d, %d, 0, 0, NULL, %d, 0, 16877, 1700000000, '', NULL)",
                rootHash.hash1(), rootHash.hash2(), 1));
        conn.createStatement().executeUpdate(String.format(
                "INSERT INTO catalog VALUES (100, 200, %d, %d, X'aabb', %d, 1024, 33188, 1700000000, 'test.txt', NULL)",
                rootHash.hash1(), rootHash.hash2(), 4));

        conn.createStatement().executeUpdate(
                "CREATE TABLE nested_catalogs (path TEXT, sha1 TEXT)");
        conn.createStatement().executeUpdate(
                "CREATE TABLE chunks (md5path_1 INTEGER, md5path_2 INTEGER, " +
                        "offset INTEGER, size INTEGER, hash BLOB)");
        conn.createStatement().executeUpdate(
                "CREATE TABLE statistics (counter TEXT, value INTEGER)");
        conn.createStatement().executeUpdate(
                "INSERT INTO statistics VALUES ('self_regular', 1)");
        conn.createStatement().executeUpdate(
                "INSERT INTO statistics VALUES ('self_dir', 1)");
        conn.createStatement().executeUpdate(
                "INSERT INTO statistics VALUES ('self_file_size', 1024)");
        conn.close();
        storeCompressedDb(dbTmp, hash, "C");
    }

    private void createHistoryDb(String hash) throws Exception {
        var dbTmp = tempDir.resolve("history_tmp_" + hash + ".db");
        var conn = DriverManager.getConnection("jdbc:sqlite:" + dbTmp);
        conn.setAutoCommit(true);
        conn.createStatement().executeUpdate(
                "CREATE TABLE properties (key TEXT, value TEXT)");
        conn.createStatement().executeUpdate(
                "INSERT INTO properties VALUES ('schema', '1.0')");
        conn.createStatement().executeUpdate(
                "INSERT INTO properties VALUES ('fqrn', 'test.example.com')");
        conn.createStatement().executeUpdate(
                "CREATE TABLE tags (name TEXT, hash TEXT, revision INTEGER, " +
                        "timestamp INTEGER, channel INTEGER, description TEXT)");
        conn.createStatement().executeUpdate(
                "INSERT INTO tags VALUES ('v1', 'aabbccddee', 1, 1700000000, 0, 'First release')");
        conn.close();
        storeCompressedDb(dbTmp, hash, "H");
    }

    private byte[] compress(byte[] data) throws IOException {
        var baos = new ByteArrayOutputStream();
        try (var out = new DeflaterOutputStream(baos)) {
            out.write(data);
        }
        return baos.toByteArray();
    }

    private Repository createRepository() throws Exception {
        var fetcher = new Fetcher(sourceDir.toString(), cacheDir.toString());
        return new Repository(fetcher);
    }

    @Test
    void basicProperties() throws Exception {
        writeManifest("aabbccddee");
        createCatalogDb("aabbccddee");
        var repo = createRepository();
        assertEquals("test.example.com", repo.fqrn());
        assertEquals("test.example.com", repo.getName());
        assertEquals(1, repo.getRevisionNumber());
        assertEquals("roothash123", repo.getRootHash());
        assertEquals(1700000000L, repo.getTimestamp());
        assertNotNull(repo.manifest());
        assertNotNull(repo.fetcher());
    }

    @Test
    void hasNoHistory() throws Exception {
        writeManifest("aabbccddee");
        var repo = createRepository();
        assertFalse(repo.hasHistory());
        assertThrows(CvmfsException.class, repo::retrieveHistory);
    }

    @Test
    void hasHistory() throws Exception {
        writeManifestWithHistory("aabbccddee", "ff11223344");
        var repo = createRepository();
        assertTrue(repo.hasHistory());
    }

    @Test
    void retrieveCurrentRootCatalog() throws Exception {
        writeManifest("aabbccddee");
        createCatalogDb("aabbccddee");
        var repo = createRepository();
        var catalog = repo.retrieveCurrentRootCatalog();
        assertNotNull(catalog);
        assertTrue(catalog.isRoot());
    }

    @Test
    void catalogCaching() throws Exception {
        writeManifest("aabbccddee");
        createCatalogDb("aabbccddee");
        var repo = createRepository();
        var cat1 = repo.retrieveCurrentRootCatalog();
        var cat2 = repo.retrieveCurrentRootCatalog();
        assertSame(cat1, cat2);
        assertFalse(repo.openedCatalogs().isEmpty());
    }

    @Test
    void unloadCatalogs() throws Exception {
        writeManifest("aabbccddee");
        createCatalogDb("aabbccddee");
        var repo = createRepository();
        repo.retrieveCurrentRootCatalog();
        assertFalse(repo.openedCatalogs().isEmpty());
        assertTrue(repo.unloadCatalogs());
        assertTrue(repo.openedCatalogs().isEmpty());
    }

    @Test
    void openedCatalogsImmutable() throws Exception {
        writeManifest("aabbccddee");
        var repo = createRepository();
        assertThrows(UnsupportedOperationException.class, () ->
                repo.openedCatalogs().put("test", null));
    }

    @Test
    void lookupRoot() throws Exception {
        writeManifest("aabbccddee");
        createCatalogDb("aabbccddee");
        var repo = createRepository();
        var entry = repo.lookup("/");
        assertNotNull(entry);
        assertTrue(entry.isDirectory());
    }

    @Test
    void lookupMissingPathThrows() throws Exception {
        writeManifest("aabbccddee");
        createCatalogDb("aabbccddee");
        var repo = createRepository();
        assertThrows(CvmfsException.class, () -> repo.lookup("/nonexistent"));
    }

    @Test
    void listDirectoryRoot() throws Exception {
        writeManifest("aabbccddee");
        createCatalogDb("aabbccddee");
        var repo = createRepository();
        var entries = repo.listDirectory("/");
        assertFalse(entries.isEmpty());
        assertEquals("test.txt", entries.getFirst().name());
    }

    @Test
    void listDirectoryOnFileThrows() throws Exception {
        writeManifest("aabbccddee");
        createCatalogDb("aabbccddee");
        var repo = createRepository();
        assertThrows(CvmfsException.class, () -> repo.listDirectory("/test.txt"));
    }

    @Test
    void getStatistics() throws Exception {
        writeManifest("aabbccddee");
        createCatalogDb("aabbccddee");
        var repo = createRepository();
        var stats = repo.getStatistics();
        assertNotNull(stats);
        assertEquals(1, stats.regular());
    }

    @Test
    void retrieveHistoryAndListTags() throws Exception {
        createCatalogDb("aabbccddee");
        createHistoryDb("ff11223344");
        writeManifestWithHistory("aabbccddee", "ff11223344");
        var repo = createRepository();
        var history = repo.retrieveHistory();
        assertNotNull(history);
        var tags = history.listTags();
        assertEquals(1, tags.size());
        assertEquals("v1", tags.getFirst().name());
    }

    @Test
    void getTag() throws Exception {
        createCatalogDb("aabbccddee");
        createHistoryDb("ff11223344");
        writeManifestWithHistory("aabbccddee", "ff11223344");
        var repo = createRepository();
        var tag = repo.getTag(1);
        assertEquals("v1", tag.name());
    }

    @Test
    void getTagNotFoundThrows() throws Exception {
        createCatalogDb("aabbccddee");
        createHistoryDb("ff11223344");
        writeManifestWithHistory("aabbccddee", "ff11223344");
        var repo = createRepository();
        assertThrows(CvmfsException.class, () -> repo.getTag(999));
    }

    @Test
    void getLastTag() throws Exception {
        createCatalogDb("aabbccddee");
        createHistoryDb("ff11223344");
        writeManifestWithHistory("aabbccddee", "ff11223344");
        var repo = createRepository();
        var tag = repo.getLastTag();
        assertEquals("v1", tag.name());
    }

    @Test
    void currentTagFromHistory() throws Exception {
        createCatalogDb("aabbccddee");
        createHistoryDb("ff11223344");
        writeManifestWithHistory("aabbccddee", "ff11223344");
        var repo = createRepository();
        var tag = repo.currentTag();
        assertEquals("v1", tag.name());
        assertEquals(1, tag.revision());
    }

    @Test
    void setCurrentTag() throws Exception {
        createCatalogDb("aabbccddee");
        createHistoryDb("ff11223344");
        writeManifestWithHistory("aabbccddee", "ff11223344");
        var repo = createRepository();
        repo.setCurrentTag(1);
        var tag = repo.currentTag();
        assertEquals("v1", tag.name());
    }

    @Test
    void setCurrentTagInvalidRevisionThrows() throws Exception {
        createCatalogDb("aabbccddee");
        createHistoryDb("ff11223344");
        writeManifestWithHistory("aabbccddee", "ff11223344");
        var repo = createRepository();
        assertThrows(CvmfsException.class, () -> repo.setCurrentTag(999));
    }

    @Test
    void retrieveCatalogForPath() throws Exception {
        writeManifest("aabbccddee");
        createCatalogDb("aabbccddee");
        var repo = createRepository();
        var catalog = repo.retrieveCatalogForPath("/");
        assertNotNull(catalog);
    }

    @Test
    void getFileOnDirectoryThrows() throws Exception {
        writeManifest("aabbccddee");
        createCatalogDb("aabbccddee");
        var repo = createRepository();
        assertThrows(CvmfsException.class, () -> repo.getFile("/"));
    }

    @Test
    void openFileOnDirectoryThrows() throws Exception {
        writeManifest("aabbccddee");
        createCatalogDb("aabbccddee");
        var repo = createRepository();
        assertThrows(CvmfsException.class, () -> repo.openFile("/"));
    }

    @Test
    void missingManifestThrows() {
        assertThrows(Exception.class, () -> createRepository());
    }

    @Test
    void manifestFields() throws Exception {
        writeManifest("aabbccddee");
        var repo = createRepository();
        assertEquals("aabbccddee", repo.manifest().rootCatalog());
        assertEquals(900, repo.manifest().ttl());
    }
}
