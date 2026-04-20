package com.molina.cvmfs.manifest;

import com.molina.cvmfs.common.CvmfsException;
import com.molina.cvmfs.rootfile.RootFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class ManifestTest {

    @TempDir
    Path tempDir;

    private Manifest parseManifest(String content) throws IOException, CvmfsException {
        var file = tempDir.resolve("manifest");
        Files.writeString(file, content);
        return new Manifest(file.toFile());
    }

    private Manifest parseSignedManifest(String content) throws Exception {
        var md = MessageDigest.getInstance("SHA-1");
        var hash = HexFormat.of().formatHex(md.digest(content.getBytes(StandardCharsets.UTF_8)));
        var signed = content + "--\n" + hash + "\nsignature_data\n";
        var file = tempDir.resolve("manifest_signed");
        Files.writeString(file, signed);
        return new Manifest(file.toFile());
    }

    @Test
    void parseAllFields() throws Exception {
        var manifest = parseManifest("""
                Cmy_root_catalog
                Rmy_root_hash
                B1024
                Xmy_certificate
                Hmy_history_db
                T1000
                D3600
                S42
                Ntest.repo
                Lmy_micro_catalog
                Gyes
                Ayes
                """);

        assertEquals("my_root_catalog", manifest.rootCatalog());
        assertEquals("my_root_hash", manifest.rootHash());
        assertEquals(1024, manifest.rootCatalogSize());
        assertEquals("my_certificate", manifest.certificate());
        assertEquals("my_history_db", manifest.historyDatabase());
        assertEquals(3600, manifest.ttl());
        assertEquals(42, manifest.revision());
        assertEquals("test.repo", manifest.repositoryName());
        assertEquals("my_micro_catalog", manifest.microCatalog());
        assertEquals(1000, manifest.lastModified().getEpochSecond());
        assertTrue(manifest.garbageCollectable());
        assertTrue(manifest.allowsAlternativeName());
    }

    @Test
    void parseTimestampIsSeconds() throws Exception {
        var manifest = parseManifest("Ccat\nRhash\nB0\nXcert\nT1713952007\nD1\nS1\nNrepo\nGno\nAno\n");
        assertEquals(1713952007, manifest.lastModified().getEpochSecond());
    }

    @Test
    void parseYFieldMicroCatalog() throws Exception {
        var manifest = parseManifest("Ccat\nRhash\nB0\nXcert\nT0\nD1\nS1\nNrepo\nYmicro_hash\nGno\nAno\n");
        assertEquals("micro_hash", manifest.microCatalog());
    }

    @Test
    void parseBooleanYes() {
        assertTrue(Manifest.parseBoolean("yes"));
    }

    @Test
    void parseBooleanNo() {
        assertFalse(Manifest.parseBoolean("no"));
    }

    @Test
    void parseBooleanInvalid() {
        assertFalse(Manifest.parseBoolean("maybe"));
    }

    @Test
    void hasHistoryNone() throws Exception {
        var manifest = parseManifest("Ccat\nRhash\nB0\nXcert\nT0\nD1\nS1\nNrepo\nL\nGno\nAno\n");
        assertFalse(manifest.hasHistory());
    }

    @Test
    void hasHistorySome() throws Exception {
        var manifest = parseManifest("Ccat\nRhash\nB0\nXcert\nHhistdb\nT0\nD1\nS1\nNrepo\nL\nGno\nAno\n");
        assertTrue(manifest.hasHistory());
    }

    @Test
    void unknownKeysIgnored() throws Exception {
        var manifest = parseManifest("Ccat\nRhash\nB0\nXcert\nT0\nD1\nS1\nNrepo\nL\nGno\nAno\nZunknown\nQanother\n");
        assertNotNull(manifest);
        assertEquals("repo", manifest.repositoryName());
    }

    @Test
    void missingRootCatalogThrows() {
        assertThrows(CvmfsException.class, () ->
                parseManifest("Rhash\nB0\nXcert\nT0\nD1\nS1\nNrepo\n"));
    }

    @Test
    void missingRootHashThrows() {
        assertThrows(CvmfsException.class, () ->
                parseManifest("Ccat\nB0\nXcert\nT0\nD1\nS1\nNrepo\n"));
    }

    @Test
    void missingRevisionThrows() {
        assertThrows(CvmfsException.class, () ->
                parseManifest("Ccat\nRhash\nB0\nXcert\nT0\nD1\nNrepo\n"));
    }

    @Test
    void missingRepoNameThrows() {
        assertThrows(CvmfsException.class, () ->
                parseManifest("Ccat\nRhash\nB0\nXcert\nT0\nD1\nS1\n"));
    }
}
