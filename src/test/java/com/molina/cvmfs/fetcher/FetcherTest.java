package com.molina.cvmfs.fetcher;

import com.molina.cvmfs.common.CvmfsException;
import com.github.luben.zstd.Zstd;
import net.jpountz.lz4.LZ4FrameOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.HexFormat;
import java.util.zip.DeflaterOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class FetcherTest {

    @Test
    void verifyHashMatchingSha1() throws Exception {
        var data = "hello world".getBytes(StandardCharsets.UTF_8);
        var md = MessageDigest.getInstance("SHA-1");
        var hash = HexFormat.of().formatHex(md.digest(data));
        assertDoesNotThrow(() -> Fetcher.verifyHash(data, hash));
    }

    @Test
    void verifyHashWithSuffix() throws Exception {
        var data = "test data".getBytes(StandardCharsets.UTF_8);
        var md = MessageDigest.getInstance("SHA-1");
        var hash = HexFormat.of().formatHex(md.digest(data)) + "-rmd160";
        assertDoesNotThrow(() -> Fetcher.verifyHash(data, hash));
    }

    @Test
    void verifyHashMismatch() {
        var data = "hello".getBytes(StandardCharsets.UTF_8);
        assertThrows(CvmfsException.class, () ->
                Fetcher.verifyHash(data, "0000000000000000000000000000000000000000"));
    }

    @Test
    void decompressZlib() throws Exception {
        var original = "zlib test data".getBytes(StandardCharsets.UTF_8);
        var baos = new ByteArrayOutputStream();
        try (var out = new DeflaterOutputStream(baos)) {
            out.write(original);
        }
        var result = Fetcher.decompress(baos.toByteArray());
        assertArrayEquals(original, result);
    }

    @Test
    void decompressZstd() throws Exception {
        var original = "zstd test data".getBytes(StandardCharsets.UTF_8);
        var compressed = Zstd.compress(original);
        var result = Fetcher.decompressZstd(compressed);
        assertArrayEquals(original, result);
    }

    @Test
    void decompressLz4() throws Exception {
        var original = "lz4 test data".getBytes(StandardCharsets.UTF_8);
        var baos = new ByteArrayOutputStream();
        try (var out = new LZ4FrameOutputStream(baos)) {
            out.write(original);
        }
        var result = Fetcher.decompressLz4(baos.toByteArray());
        assertArrayEquals(original, result);
    }

    @Test
    void decompressAutoDetectsFormat() throws Exception {
        var original = "auto detect test".getBytes(StandardCharsets.UTF_8);

        var zlibBaos = new ByteArrayOutputStream();
        try (var out = new DeflaterOutputStream(zlibBaos)) {
            out.write(original);
        }
        assertArrayEquals(original, Fetcher.decompress(zlibBaos.toByteArray()));

        var zstdCompressed = Zstd.compress(original);
        assertArrayEquals(original, Fetcher.decompress(zstdCompressed));

        var lz4Baos = new ByteArrayOutputStream();
        try (var out = new LZ4FrameOutputStream(lz4Baos)) {
            out.write(original);
        }
        assertArrayEquals(original, Fetcher.decompress(lz4Baos.toByteArray()));
    }

    @Test
    void decompressInvalidDataThrows() {
        var garbage = new byte[]{0x01, 0x02, 0x03, 0x04};
        assertThrows(IOException.class, () -> Fetcher.decompress(garbage));
    }

    @Test
    void withMirrors(@TempDir Path tempDir) throws Exception {
        Files.createDirectories(tempDir.resolve("data"));
        var fetcher = Fetcher.withMirrors(
                List.of("http://primary.example.com", "http://mirror1.example.com"),
                tempDir.toString());
        assertEquals("http://primary.example.com", fetcher.source());
        assertEquals(1, fetcher.mirrors().size());
        assertEquals("http://mirror1.example.com", fetcher.mirrors().getFirst());
    }

    @Test
    void withMirrorsEmptyThrows(@TempDir Path tempDir) {
        assertThrows(CvmfsException.class, () ->
                Fetcher.withMirrors(List.of(), tempDir.toString()));
    }

    @Test
    void localDirSourcePrefixed(@TempDir Path tempDir) throws Exception {
        Files.createDirectories(tempDir.resolve("data"));
        var fetcher = new Fetcher(tempDir.toString(), tempDir.toString());
        assertTrue(fetcher.source().startsWith("file://"));
    }

    @Test
    void setProxy(@TempDir Path tempDir) throws Exception {
        Files.createDirectories(tempDir.resolve("data"));
        var fetcher = new Fetcher("http://example.com", tempDir.toString());
        fetcher.setProxy("http://proxy.example.com:8080");
        assertFalse(fetcher.isOffline());
        assertEquals(0, fetcher.ioErrorCount());
    }

    @Test
    void setSources(@TempDir Path tempDir) throws Exception {
        Files.createDirectories(tempDir.resolve("data"));
        var fetcher = Fetcher.withMirrors(
                List.of("http://a.com", "http://b.com", "http://c.com"),
                tempDir.toString());
        fetcher.setSources(List.of("http://c.com", "http://a.com", "http://b.com"));
        assertEquals("http://c.com", fetcher.source());
        assertEquals(List.of("http://a.com", "http://b.com"), fetcher.mirrors());
    }

    @Test
    void mirrorsImmutable(@TempDir Path tempDir) throws Exception {
        Files.createDirectories(tempDir.resolve("data"));
        var fetcher = Fetcher.withMirrors(
                List.of("http://a.com", "http://b.com"),
                tempDir.toString());
        assertThrows(UnsupportedOperationException.class, () ->
                fetcher.mirrors().add("http://c.com"));
    }
}
