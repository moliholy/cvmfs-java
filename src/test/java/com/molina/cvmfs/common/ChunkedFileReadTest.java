package com.molina.cvmfs.common;

import com.molina.cvmfs.directoryentry.Chunk;
import com.molina.cvmfs.directoryentry.ContentHashTypes;
import com.molina.cvmfs.fetcher.Fetcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.DeflaterOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ChunkedFileReadTest {

    @TempDir
    Path tempDir;

    private Fetcher createFetcherWithChunk(String content, String hash) throws Exception {
        var cacheDir = tempDir.resolve("cache");
        var prefix = hash.substring(0, 2);
        var suffix = hash.substring(2);
        Files.createDirectories(cacheDir.resolve("data/" + prefix));
        Files.writeString(cacheDir.resolve("data/" + prefix + "/" + suffix), content);
        return new Fetcher("http://unused.local", cacheDir.toString());
    }

    @Test
    void readSingleChunk() throws Exception {
        var content = "Hello World!";
        var contentBytes = content.getBytes(StandardCharsets.UTF_8);
        var md = MessageDigest.getInstance("SHA-1");
        var hash = HexFormat.of().formatHex(md.digest(contentBytes));
        var hashBytes = HexFormat.of().parseHex(hash);

        var fetcher = createFetcherWithChunk(content, hash);
        var chunks = List.of(new Chunk(0, contentBytes.length, hashBytes, ContentHashTypes.SHA1));

        try (var cf = new ChunkedFile(chunks, contentBytes.length, fetcher)) {
            var buf = new byte[contentBytes.length];
            int read = cf.readAt(0, buf);
            assertEquals(contentBytes.length, read);
            assertEquals(content, new String(buf));
        }
    }

    @Test
    void readPartialChunk() throws Exception {
        var content = "Hello World!";
        var contentBytes = content.getBytes(StandardCharsets.UTF_8);
        var md = MessageDigest.getInstance("SHA-1");
        var hash = HexFormat.of().formatHex(md.digest(contentBytes));
        var hashBytes = HexFormat.of().parseHex(hash);

        var fetcher = createFetcherWithChunk(content, hash);
        var chunks = List.of(new Chunk(0, contentBytes.length, hashBytes, ContentHashTypes.SHA1));

        try (var cf = new ChunkedFile(chunks, contentBytes.length, fetcher)) {
            var buf = new byte[5];
            int read = cf.readAt(0, buf);
            assertEquals(5, read);
            assertEquals("Hello", new String(buf));
        }
    }

    @Test
    void readFromOffset() throws Exception {
        var content = "Hello World!";
        var contentBytes = content.getBytes(StandardCharsets.UTF_8);
        var md = MessageDigest.getInstance("SHA-1");
        var hash = HexFormat.of().formatHex(md.digest(contentBytes));
        var hashBytes = HexFormat.of().parseHex(hash);

        var fetcher = createFetcherWithChunk(content, hash);
        var chunks = List.of(new Chunk(0, contentBytes.length, hashBytes, ContentHashTypes.SHA1));

        try (var cf = new ChunkedFile(chunks, contentBytes.length, fetcher)) {
            var buf = new byte[6];
            int read = cf.readAt(6, buf);
            assertEquals(6, read);
            assertEquals("World!", new String(buf));
        }
    }

    @Test
    void readAcrossTwoChunks() throws Exception {
        var content1 = "AAAA";
        var content2 = "BBBB";
        var md = MessageDigest.getInstance("SHA-1");

        var hash1 = HexFormat.of().formatHex(md.digest(content1.getBytes()));
        md.reset();
        var hash2 = HexFormat.of().formatHex(md.digest(content2.getBytes()));

        var cacheDir = tempDir.resolve("cache");
        Files.createDirectories(cacheDir.resolve("data/" + hash1.substring(0, 2)));
        Files.createDirectories(cacheDir.resolve("data/" + hash2.substring(0, 2)));
        Files.writeString(cacheDir.resolve("data/" + hash1.substring(0, 2) + "/" + hash1.substring(2)), content1);
        Files.writeString(cacheDir.resolve("data/" + hash2.substring(0, 2) + "/" + hash2.substring(2)), content2);

        var fetcher = new Fetcher("http://unused.local", cacheDir.toString());
        var chunks = List.of(
                new Chunk(0, 4, HexFormat.of().parseHex(hash1), ContentHashTypes.SHA1),
                new Chunk(4, 4, HexFormat.of().parseHex(hash2), ContentHashTypes.SHA1));

        try (var cf = new ChunkedFile(chunks, 8, fetcher)) {
            var buf = new byte[8];
            int read = cf.readAt(0, buf);
            assertEquals(8, read);
            assertEquals("AAAABBBB", new String(buf));
        }
    }

    @Test
    void closeReleasesResources() throws Exception {
        var content = "test";
        var md = MessageDigest.getInstance("SHA-1");
        var hash = HexFormat.of().formatHex(md.digest(content.getBytes()));
        var hashBytes = HexFormat.of().parseHex(hash);

        var fetcher = createFetcherWithChunk(content, hash);
        var chunks = List.of(new Chunk(0, 4, hashBytes, ContentHashTypes.SHA1));

        var cf = new ChunkedFile(chunks, 4, fetcher);
        cf.readAt(0, new byte[4]);
        cf.close();
    }
}
