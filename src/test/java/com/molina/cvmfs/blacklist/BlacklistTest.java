package com.molina.cvmfs.blacklist;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BlacklistTest {

    @Test
    void parseFingerprints() {
        var bl = Blacklist.parse("""
                AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD
                11:22:33:44:55:66:77:88:99:00:AA:BB:CC:DD:EE:FF:00:11:22:33
                """);
        assertEquals(2, bl.fingerprints().size());
        assertTrue(bl.isFingerprintBlocked("AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD"));
        assertFalse(bl.isFingerprintBlocked("FF:FF:FF:FF"));
    }

    @Test
    void parseRevisionBlocks() {
        var bl = Blacklist.parse("""
                <repo.cern.ch 100
                <other.cern.ch 50
                """);
        assertEquals(2, bl.revisionBlockCount());
        assertTrue(bl.isRevisionBlocked("repo.cern.ch", 99));
        assertFalse(bl.isRevisionBlocked("repo.cern.ch", 100));
        assertFalse(bl.isRevisionBlocked("repo.cern.ch", 101));
        assertTrue(bl.isRevisionBlocked("other.cern.ch", 10));
    }

    @Test
    void parseComments() {
        var bl = Blacklist.parse("""
                # this is a comment
                AA:BB:CC
                # another comment
                """);
        assertEquals(1, bl.fingerprints().size());
    }

    @Test
    void parseEmpty() {
        var bl = Blacklist.parse("");
        assertTrue(bl.fingerprints().isEmpty());
        assertEquals(0, bl.revisionBlockCount());
    }

    @Test
    void parseMixed() {
        var bl = Blacklist.parse("""
                # Blocked certs
                AA:BB:CC:DD
                EE:FF:00:11
                # Blocked revisions
                <repo.cern.ch 200
                """);
        assertEquals(2, bl.fingerprints().size());
        assertEquals(1, bl.revisionBlockCount());
    }

    @Test
    void emptyBlacklist() {
        var bl = Blacklist.empty();
        assertFalse(bl.isFingerprintBlocked("anything"));
        assertFalse(bl.isRevisionBlocked("any.repo", 1));
    }

    @Test
    void loadFromFile(@TempDir Path tempDir) throws Exception {
        var file = tempDir.resolve("blacklist");
        Files.writeString(file, "AA:BB:CC\n<repo.ch 50\n");
        var bl = Blacklist.load(file.toString());
        assertEquals(1, bl.fingerprints().size());
        assertTrue(bl.isRevisionBlocked("repo.ch", 10));
    }

    @Test
    void loadNonexistentReturnsEmpty() throws Exception {
        var bl = Blacklist.load("/nonexistent/blacklist");
        assertTrue(bl.fingerprints().isEmpty());
    }

    @Test
    void loadDefaultDoesNotThrow() {
        assertDoesNotThrow(Blacklist::loadDefault);
    }

    @Test
    void fingerprintsImmutable() {
        var bl = Blacklist.parse("AA:BB");
        assertThrows(UnsupportedOperationException.class, () -> bl.fingerprints().add("CC:DD"));
    }

    @Test
    void invalidRevisionLineIgnored() {
        var bl = Blacklist.parse("<repo.ch notanumber\n");
        assertEquals(0, bl.revisionBlockCount());
    }
}
