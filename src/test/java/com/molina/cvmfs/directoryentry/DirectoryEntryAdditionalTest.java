package com.molina.cvmfs.directoryentry;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DirectoryEntryAdditionalTest {

    private DirectoryEntry makeEntry(int flags, String contentHash, String name,
                                     String symlink, long size) {
        return new DirectoryEntry(
                1, 2, 3, 4, contentHash, flags,
                size, 0644, 1700000000, name, symlink,
                1000, 1000, 1, new ArrayList<>()
        );
    }

    @Test
    void accessors() {
        var entry = new DirectoryEntry(10, 20, 30, 40, "hash", Flags.FILE,
                512, 33188, 1700000000, "myfile.txt", "/link/target",
                500, 600, 3, List.of());
        assertEquals(10, entry.md5path1());
        assertEquals(20, entry.md5path2());
        assertEquals(30, entry.parent1());
        assertEquals(40, entry.parent2());
        assertEquals("hash", entry.contentHash());
        assertEquals(Flags.FILE, entry.flags());
        assertEquals(512, entry.size());
        assertEquals(33188, entry.mode());
        assertEquals(1700000000, entry.mtime());
        assertEquals("myfile.txt", entry.name());
        assertEquals("/link/target", entry.symlink());
        assertEquals(500, entry.uid());
        assertEquals(600, entry.gid());
        assertEquals(3, entry.hardlinks());
        assertEquals(ContentHashTypes.SHA1, entry.contentHashType());
        assertTrue(entry.chunks().isEmpty());
    }

    @Test
    void nullSymlink() {
        var entry = makeEntry(Flags.FILE, "h", "test", null, 100);
        assertNull(entry.symlink());
    }

    @Test
    void symlinkPresent() {
        var entry = makeEntry(Flags.LINK, null, "link", "/target", 0);
        assertEquals("/target", entry.symlink());
        assertTrue(entry.isSymlink());
    }

    @Test
    void catalogDatabaseFields() {
        var fields = DirectoryEntry.catalogDatabaseFields();
        assertTrue(fields.contains("md5path_1"));
        assertTrue(fields.contains("md5path_2"));
        assertTrue(fields.contains("flags"));
        assertTrue(fields.contains("name"));
        assertTrue(fields.contains("symlink"));
    }

    @Test
    void constructorWithNullChunks() {
        var entry = new DirectoryEntry(1, 2, 3, 4, "h", Flags.FILE,
                100, 0644, 1000, "f", null, 0, 0, 1, null);
        assertNotNull(entry.chunks());
        assertTrue(entry.chunks().isEmpty());
    }

    @Test
    void constructorWithChunks() {
        var chunks = List.of(
                new Chunk(0, 100, new byte[]{1, 2}, ContentHashTypes.SHA1),
                new Chunk(100, 200, new byte[]{3, 4}, ContentHashTypes.SHA1));
        var entry = new DirectoryEntry(1, 2, 3, 4, null, Flags.FILE,
                300, 0644, 1000, "f", null, 0, 0, 1, chunks);
        assertEquals(2, entry.chunks().size());
        assertTrue(entry.hasChunks());
    }

    @Test
    void contentHashTypeSha256() {
        int flags = Flags.FILE | (2 << 8);
        var entry = makeEntry(flags, "hash", "f", null, 100);
        assertEquals(ContentHashTypes.SHA256, entry.contentHashType());
        var hashStr = entry.contentHashString();
        assertTrue(hashStr.isPresent());
        assertTrue(hashStr.get().endsWith("-sha256"));
    }

    @Test
    void contentHashTypeShake128() {
        int flags = Flags.FILE | (3 << 8);
        var entry = makeEntry(flags, "hash", "f", null, 100);
        assertEquals(ContentHashTypes.SHAKE128, entry.contentHashType());
    }

    @Test
    void hardlinkGroupAndNlink() {
        long hardlinks = (7L << 32) | 5;
        var entry = new DirectoryEntry(1, 2, 3, 4, "h", Flags.FILE,
                100, 0644, 1000, "f", null, 0, 0, hardlinks, null);
        assertEquals(5, entry.nlink());
        assertEquals(7, entry.hardlinkGroup());
    }

    @Test
    void zeroHardlinks() {
        var entry = new DirectoryEntry(1, 2, 3, 4, "h", Flags.FILE,
                100, 0644, 1000, "f", null, 0, 0, 0, null);
        assertEquals(0, entry.nlink());
        assertEquals(0, entry.hardlinkGroup());
    }

    @Test
    void directoryWithNestedFlags() {
        int flags = Flags.DIRECTORY | Flags.NESTED_CATALOG_MOUNTPOINT;
        var entry = makeEntry(flags, "h", "nested", null, 0);
        assertTrue(entry.isDirectory());
        assertTrue(entry.isNestedCatalogMountpoint());
        assertFalse(entry.isFile());
    }
}
