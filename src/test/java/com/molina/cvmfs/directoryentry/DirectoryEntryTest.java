package com.molina.cvmfs.directoryentry;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DirectoryEntryTest {

    private DirectoryEntry makeEntry(int flags, String contentHash) {
        return new DirectoryEntry(
                1, 2, 3, 4, contentHash, flags,
                100, 0644, 1000, "test", null,
                1000, 1000, 1, new ArrayList<>()
        );
    }

    @Test
    void entryIsDirectory() {
        var entry = makeEntry(Flags.DIRECTORY, "hash");
        assertTrue(entry.isDirectory());
        assertFalse(entry.isFile());
        assertFalse(entry.isSymlink());
    }

    @Test
    void entryIsFile() {
        var entry = makeEntry(Flags.FILE, "hash");
        assertTrue(entry.isFile());
        assertFalse(entry.isDirectory());
        assertFalse(entry.isSymlink());
    }

    @Test
    void entryIsSymlink() {
        var entry = makeEntry(Flags.LINK, "hash");
        assertTrue(entry.isSymlink());
        assertFalse(entry.isFile());
        assertFalse(entry.isDirectory());
    }

    @Test
    void entryIsNestedCatalogMountpoint() {
        var entry = makeEntry(Flags.NESTED_CATALOG_MOUNTPOINT, "hash");
        assertTrue(entry.isNestedCatalogMountpoint());
    }

    @Test
    void entryIsNestedCatalogRoot() {
        var entry = makeEntry(Flags.NESTED_CATALOG_ROOT, "hash");
        assertTrue(entry.isNestedCatalogRoot());
    }

    @Test
    void hasChunksWhenNoContentHash() {
        var entry = makeEntry(Flags.FILE, null);
        assertTrue(entry.hasChunks());
    }

    @Test
    void noChunksWhenContentHashPresent() {
        var entry = makeEntry(Flags.FILE, "abc");
        assertFalse(entry.hasChunks());
    }

    @Test
    void contentHashStringWithSha1() {
        var entry = makeEntry(Flags.FILE, "abc123");
        var result = entry.contentHashString();
        assertTrue(result.isPresent());
        assertEquals("abc123", result.get());
    }

    @Test
    void contentHashStringWithRipemd160() {
        int flags = Flags.FILE | 256;
        var entry = makeEntry(flags, "abc123");
        var result = entry.contentHashString();
        assertTrue(result.isPresent());
        assertEquals("abc123-rmd160", result.get());
    }

    @Test
    void contentHashStringNoneForChunked() {
        var entry = makeEntry(Flags.FILE, null);
        assertTrue(entry.contentHashString().isEmpty());
    }

    @Test
    void pathHash() {
        var entry = makeEntry(Flags.FILE, "h");
        var ph = entry.pathHash();
        assertEquals(1, ph.hash1());
        assertEquals(2, ph.hash2());
    }

    @Test
    void parentHash() {
        var entry = makeEntry(Flags.FILE, "h");
        var ph = entry.parentHash();
        assertEquals(3, ph.hash1());
        assertEquals(4, ph.hash2());
    }

    @Test
    void readContentHashTypeSha1FromZero() {
        assertEquals(ContentHashTypes.SHA1, DirectoryEntry.readContentHashType(0));
    }

    @Test
    void readContentHashTypeRipemd160From256() {
        assertEquals(ContentHashTypes.RIPEMD160, DirectoryEntry.readContentHashType(256));
    }

    @Test
    void readContentHashTypeSha256From512() {
        assertEquals(ContentHashTypes.SHA256, DirectoryEntry.readContentHashType(512));
    }

    @Test
    void readContentHashTypeShake128From768() {
        assertEquals(ContentHashTypes.SHAKE128, DirectoryEntry.readContentHashType(768));
    }

    @Test
    void readContentHashTypeIgnoresNonHashFlags() {
        assertEquals(ContentHashTypes.SHA1, DirectoryEntry.readContentHashType(Flags.FILE));
    }

    @Test
    void combinedFlags() {
        int flags = Flags.FILE | Flags.FILE_CHUNK;
        var entry = makeEntry(flags, null);
        assertTrue(entry.isFile());
        assertFalse(entry.isDirectory());
        assertTrue(entry.hasChunks());
    }

    @Test
    void nlink() {
        var entry = new DirectoryEntry(1, 2, 3, 4, "h", Flags.FILE,
                100, 0644, 1000, "test", null, 1000, 1000, 3, null);
        assertEquals(3, entry.nlink());
    }

    @Test
    void hardlinkGroup() {
        long hardlinks = (5L << 32) | 2;
        var entry = new DirectoryEntry(1, 2, 3, 4, "h", Flags.FILE,
                100, 0644, 1000, "test", null, 1000, 1000, hardlinks, null);
        assertEquals(2, entry.nlink());
        assertEquals(5, entry.hardlinkGroup());
    }

    @Test
    void uidGid() {
        var entry = makeEntry(Flags.FILE, "h");
        assertEquals(1000, entry.uid());
        assertEquals(1000, entry.gid());
    }

    @Test
    void isExternalFile() {
        int flags = Flags.FILE | Flags.EXTERNAL_FILE;
        var entry = makeEntry(flags, "h");
        assertTrue(entry.isExternalFile());
        assertTrue(entry.isFile());
    }

    @Test
    void isNotExternalFile() {
        var entry = makeEntry(Flags.FILE, "h");
        assertFalse(entry.isExternalFile());
    }
}
