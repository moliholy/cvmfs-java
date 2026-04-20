package com.molina.cvmfs.repository;

import com.molina.cvmfs.common.CvmfsException;
import com.molina.cvmfs.fetcher.Fetcher;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
class RepositoryIntegrationTest {

    private static final String REPO_URL = "http://cvmfs-stratum-one.cern.ch/opt/boss";
    private static final String CACHE_PATH = "/tmp/cvmfs_test_cache";
    private static final int PINNED_REVISION = 293;

    private static Repository createRepo() throws IOException, CvmfsException {
        Files.createDirectories(Path.of(CACHE_PATH));
        var fetcher = new Fetcher(REPO_URL, CACHE_PATH, true);
        var repo = new Repository(fetcher);
        repo.setCurrentTag(PINNED_REVISION);
        return repo;
    }

    @BeforeAll
    static void ensureCacheDir() throws IOException {
        Files.createDirectories(Path.of(CACHE_PATH));
    }

    // --- Repository initialization ---

    @Test
    void initialization() throws Exception {
        var repo = createRepo();
        assertEquals(0, repo.openedCatalogs().size());
        assertEquals("boss.cern.ch", repo.fqrn());
        repo.retrieveCurrentRootCatalog();
        assertEquals(1, repo.openedCatalogs().size());
    }

    @Test
    void revisionInfo() throws Exception {
        var repo = createRepo();
        assertTrue(repo.getRevisionNumber() > 0);
        assertFalse(repo.getRootHash().isEmpty());
        assertFalse(repo.getName().isEmpty());
        assertTrue(repo.getTimestamp() > 0);
    }

    @Test
    void currentTag() throws Exception {
        var repo = createRepo();
        var tag = repo.currentTag();
        assertTrue(tag.revision() > 0);
        assertFalse(tag.hash().isEmpty());
        assertTrue(tag.timestamp() > 0);
    }

    // --- History and tags ---

    @Test
    void hasHistory() throws Exception {
        var repo = createRepo();
        assertTrue(repo.hasHistory());
    }

    @Test
    void retrieveHistory() throws Exception {
        var repo = createRepo();
        var history = repo.retrieveHistory();
        assertEquals("1.0", history.schema());
        assertEquals("boss.cern.ch", history.fqrn());
    }

    @Test
    void getTagByRevision() throws Exception {
        var repo = createRepo();
        int rev = repo.getRevisionNumber();
        var tag = repo.getTag(rev);
        assertEquals(rev, tag.revision());
        assertFalse(tag.hash().isEmpty());
    }

    @Test
    void getLastTag() throws Exception {
        var repo = createRepo();
        var tag = repo.getLastTag();
        assertTrue(tag.revision() > 0);
        assertFalse(tag.hash().isEmpty());
    }

    @Test
    void setCurrentTag() throws Exception {
        var repo = createRepo();
        int rev = repo.getRevisionNumber();
        repo.setCurrentTag(rev);
        var tag = repo.currentTag();
        assertEquals(rev, tag.revision());
    }

    // --- History queries ---

    @Test
    void historyGetTagByName() throws Exception {
        var repo = createRepo();
        var history = repo.retrieveHistory();
        var tag = history.getTagByName("trunk");
        assertTrue(tag.isPresent());
        assertEquals("trunk", tag.get().name());
    }

    @Test
    void historyGetTagByRevision() throws Exception {
        var repo = createRepo();
        var history = repo.retrieveHistory();
        int rev = repo.currentTag().revision();
        var tag = history.getTagByRevision(rev);
        assertTrue(tag.isPresent());
        assertEquals(rev, tag.get().revision());
    }

    @Test
    void historyGetTagByDate() throws Exception {
        var repo = createRepo();
        var history = repo.retrieveHistory();
        var tag = history.getTagByDate(0);
        assertTrue(tag.isPresent());
    }

    @Test
    void historyGetNonexistentName() throws Exception {
        var repo = createRepo();
        var history = repo.retrieveHistory();
        var tag = history.getTagByName("this_tag_does_not_exist_at_all");
        assertTrue(tag.isEmpty());
    }

    @Test
    void historyGetNonexistentRevision() throws Exception {
        var repo = createRepo();
        var history = repo.retrieveHistory();
        var tag = history.getTagByRevision(999_999_999);
        assertTrue(tag.isEmpty());
    }

    // --- Catalog operations ---

    @Test
    void retrieveRootCatalog() throws Exception {
        var repo = createRepo();
        var catalog = repo.retrieveCurrentRootCatalog();
        assertTrue(catalog.isRoot());
        assertTrue(catalog.revision() > 0);
        assertTrue(catalog.schema() > 0);
    }

    @Test
    void catalogCaching() throws Exception {
        var repo = createRepo();
        var hash = repo.currentTag().hash();
        repo.retrieveCatalog(hash);
        assertEquals(1, repo.openedCatalogs().size());
        repo.retrieveCatalog(hash);
        assertEquals(1, repo.openedCatalogs().size());
    }

    @Test
    void catalogHasNested() throws Exception {
        var repo = createRepo();
        var catalog = repo.retrieveCurrentRootCatalog();
        assertTrue(catalog.hasNested());
        assertTrue(catalog.nestedCount() > 0);
    }

    @Test
    void catalogListNested() throws Exception {
        var repo = createRepo();
        var catalog = repo.retrieveCurrentRootCatalog();
        var nested = catalog.listNested();
        assertFalse(nested.isEmpty());
        for (var ref : nested) {
            assertFalse(ref.rootPath().isEmpty());
            assertFalse(ref.catalogHash().isEmpty());
        }
    }

    @Test
    void retrieveCatalogForPath() throws Exception {
        var repo = createRepo();
        var catalog = repo.retrieveCatalogForPath("");
        assertTrue(catalog.isRoot());
        var nestedCatalog = repo.retrieveCatalogForPath("/slc4_ia32_gcc34");
        assertFalse(nestedCatalog.isRoot());
    }

    // --- Statistics ---

    @Test
    void statistics() throws Exception {
        var repo = createRepo();
        var stats = repo.getStatistics();
        assertTrue(stats.dir() > 0);
        assertTrue(stats.regular() > 0);
        assertTrue(stats.fileSize() > 0);
    }

    // --- Directory listing ---

    @Test
    void listRootDirectory() throws Exception {
        var repo = createRepo();
        var entries = repo.listDirectory("/");
        assertFalse(entries.isEmpty());
        var names = entries.stream().map(e -> e.name()).toList();
        assertTrue(names.contains("testfile"));
        assertTrue(names.contains("database"));
    }

    @Test
    void listSubdirectory() throws Exception {
        var repo = createRepo();
        var entries = repo.listDirectory("/database");
        assertFalse(entries.isEmpty());
        var names = entries.stream().map(e -> e.name()).toList();
        assertTrue(names.contains("offlinedb.db"));
        assertTrue(names.contains("run.db"));
    }

    @Test
    void listNestedCatalogDirectory() throws Exception {
        var repo = createRepo();
        var entries = repo.listDirectory("/slc4_ia32_gcc34");
        assertFalse(entries.isEmpty());
    }

    @Test
    void listNonexistentDirectory() throws Exception {
        var repo = createRepo();
        assertThrows(CvmfsException.class, () -> repo.listDirectory("/nonexistent_path_xyz"));
    }

    // --- Lookup ---

    @Test
    void lookupRoot() throws Exception {
        var repo = createRepo();
        var entry = repo.lookup("/");
        assertTrue(entry.isDirectory());
    }

    @Test
    void lookupFile() throws Exception {
        var repo = createRepo();
        var entry = repo.lookup("/testfile");
        assertTrue(entry.isFile());
        assertEquals("testfile", entry.name());
        assertEquals(50, entry.size());
    }

    @Test
    void lookupDirectory() throws Exception {
        var repo = createRepo();
        var entry = repo.lookup("/database");
        assertTrue(entry.isDirectory());
        assertEquals("database", entry.name());
    }

    @Test
    void lookupSymlink() throws Exception {
        var repo = createRepo();
        var entry = repo.lookup("/pacman-3.29/setup.csh");
        assertTrue(entry.isSymlink());
        assertEquals("scripts/initialize_setup.csh", entry.symlink());
    }

    @Test
    void lookupNestedCatalogEntry() throws Exception {
        var repo = createRepo();
        var entry = repo.lookup("/slc4_ia32_gcc34");
        assertTrue(entry.isDirectory());
        assertTrue(entry.isNestedCatalogMountpoint() || entry.isNestedCatalogRoot());
    }

    @Test
    void lookupNonexistent() throws Exception {
        var repo = createRepo();
        assertThrows(CvmfsException.class, () -> repo.lookup("/this/does/not/exist"));
    }

    // --- File reading ---

    @Test
    void getFileRegular() throws Exception {
        var repo = createRepo();
        var path = repo.getFile("/testfile");
        var content = Files.readString(path);
        assertEquals(50, content.length());
        assertTrue(content.contains("slc4_ia32_gcc34"));
    }

    @Test
    void getFileBinary() throws Exception {
        var repo = createRepo();
        var path = repo.getFile("/pacman-latest.tar.gz");
        var header = Files.readAllBytes(path);
        assertEquals((byte) 0x1f, header[0]);
        assertEquals((byte) 0x8b, header[1]);
    }

    @Test
    void getFileNotAFile() throws Exception {
        var repo = createRepo();
        assertThrows(CvmfsException.class, () -> repo.getFile("/database"));
    }

    @Test
    void getFileNonexistent() throws Exception {
        var repo = createRepo();
        assertThrows(CvmfsException.class, () -> repo.getFile("/nonexistent_file"));
    }

    // --- DirectoryEntry properties ---

    @Test
    void directoryEntryFileAttributes() throws Exception {
        var repo = createRepo();
        var entry = repo.lookup("/testfile");
        assertTrue(entry.isFile());
        assertFalse(entry.isDirectory());
        assertFalse(entry.isSymlink());
        assertFalse(entry.hasChunks());
        assertTrue(entry.contentHash() != null);
        assertTrue(entry.contentHashString().isPresent());
        assertTrue(entry.mode() > 0);
        assertTrue(entry.mtime() > 0);
        assertTrue(entry.nlink() > 0);
    }

    @Test
    void directoryEntryChunkedAttributes() throws Exception {
        var repo = createRepo();
        var entry = repo.lookup("/database/offlinedb.db");
        assertTrue(entry.isFile());
        assertTrue(entry.hasChunks());
        assertFalse(entry.chunks().isEmpty());
        assertNull(entry.contentHash());
        assertTrue(entry.contentHashString().isEmpty());
        for (var chunk : entry.chunks()) {
            assertFalse(chunk.contentHash().isEmpty());
            assertTrue(chunk.size() > 0);
        }
    }

    @Test
    void directoryEntrySymlinkAttributes() throws Exception {
        var repo = createRepo();
        var entry = repo.lookup("/pacman-3.29/setup.csh");
        assertTrue(entry.isSymlink());
        assertFalse(entry.isDirectory());
        assertNotNull(entry.symlink());
    }

    // --- Fetcher ---

    @Test
    void fetcherNewHttp() throws Exception {
        var fetcher = new Fetcher(REPO_URL, CACHE_PATH, true);
        assertTrue(fetcher.source().startsWith("http"));
    }

    @Test
    void fetcherNewLocalDir() throws Exception {
        var tmp = Path.of("/tmp/cvmfs_test_fetcher_local_" + ProcessHandle.current().pid());
        Files.createDirectories(tmp);
        var fetcher = new Fetcher(tmp.toString(), CACHE_PATH, true);
        assertTrue(fetcher.source().startsWith("file://"));
        Files.deleteIfExists(tmp);
    }

    @Test
    void fetcherRetrieveRawFile() throws Exception {
        var fetcher = new Fetcher(REPO_URL, CACHE_PATH, true);
        var path = fetcher.retrieveRawFile(".cvmfspublished");
        assertTrue(Files.exists(path));
        var content = Files.readString(path, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertFalse(content.isEmpty());
        assertTrue(content.contains("boss.cern.ch"));
    }

    // --- Catalog direct operations ---

    @Test
    void catalogFindDirectoryEntry() throws Exception {
        var repo = createRepo();
        var catalog = repo.retrieveCurrentRootCatalog();
        var entry = catalog.findDirectoryEntry("");
        assertTrue(entry.isPresent());
        assertTrue(entry.get().isDirectory());
    }

    @Test
    void catalogListDirectory() throws Exception {
        var repo = createRepo();
        var catalog = repo.retrieveCurrentRootCatalog();
        var entries = catalog.listDirectory("/");
        assertFalse(entries.isEmpty());
    }

    @Test
    void catalogStatistics() throws Exception {
        var repo = createRepo();
        var catalog = repo.retrieveCurrentRootCatalog();
        var stats = catalog.getStatistics();
        assertTrue(stats.dir() > 0);
        assertTrue(stats.regular() > 0);
        assertTrue(stats.fileSize() > 0);
    }
}
