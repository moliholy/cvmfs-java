package com.molina.cvmfs.whitelist;

import com.molina.cvmfs.common.CvmfsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WhitelistTest {

    @TempDir
    Path tempDir;

    private Whitelist parse(String content) throws IOException, CvmfsException {
        var file = tempDir.resolve("whitelist");
        Files.writeString(file, content);
        return new Whitelist(file.toFile());
    }

    @Test
    void parseWhitelist() throws Exception {
        var wl = parse("""
                20260330131219
                E20260729131219
                Nboss.cern.ch
                18:81:35:37:A7:2C:31:DB:4E:2A:6A:96:EC:A8:D4:27:06:31:5E:2F
                82:B5:70:A7:C7:CD:77:07:62:58:91:0A:E3:5E:F5:5C:1E:72:CF:CF
                --
                fakechecksum
                """);
        assertEquals("boss.cern.ch", wl.repositoryName());
        assertEquals(2, wl.fingerprints().size());
        assertTrue(wl.fingerprints().getFirst().contains("18:81:35:37"));
    }

    @Test
    void parseTimestamps() throws Exception {
        var wl = parse("""
                20260330131219
                E20260729131219
                Nboss.cern.ch
                18:81:35:37:A7:2C:31:DB:4E:2A:6A:96:EC:A8:D4:27:06:31:5E:2F
                """);
        assertNotNull(wl.created());
        assertNotNull(wl.expires());
    }

    @Test
    void matchesRepository() throws Exception {
        var wl = parse("""
                20260330131219
                E20260729131219
                Nboss.cern.ch
                18:81:35:37:A7:2C:31:DB:4E:2A:6A:96:EC:A8:D4:27:06:31:5E:2F
                """);
        assertTrue(wl.matchesRepository("boss.cern.ch"));
        assertFalse(wl.matchesRepository("other.cern.ch"));
    }

    @Test
    void missingExpiryThrows() {
        assertThrows(CvmfsException.class, () -> parse("20260330131219\n"));
    }

    @Test
    void missingNameThrows() {
        assertThrows(CvmfsException.class, () -> parse("""
                20260330131219
                E20260729131219
                """));
    }
}
