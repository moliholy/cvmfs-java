package com.molina.cvmfs.fetcher;

import com.molina.cvmfs.common.CvmfsException;
import com.github.luben.zstd.Zstd;
import net.jpountz.lz4.LZ4FrameOutputStream;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
}
