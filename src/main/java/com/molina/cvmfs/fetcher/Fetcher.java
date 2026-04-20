package com.molina.cvmfs.fetcher;

import com.molina.cvmfs.common.CvmfsException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.zip.InflaterInputStream;

public class Fetcher {
    private final Cache cache;
    private final String source;
    private final boolean verifyHash;
    private final HttpClient httpClient;

    public Fetcher(String source, String cacheDirectory, boolean verifyHash) throws IOException, CvmfsException {
        this.cache = new Cache(cacheDirectory);
        this.verifyHash = verifyHash;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(60))
                .build();

        var path = Path.of(source);
        if (Files.isDirectory(path)) {
            this.source = "file://" + path.toAbsolutePath();
        } else {
            this.source = source;
        }
    }

    public Fetcher(String source, String cacheDirectory) throws IOException, CvmfsException {
        this(source, cacheDirectory, true);
    }

    public String source() { return source; }

    public Path retrieveRawFile(String fileName) throws IOException {
        var target = cache.add(fileName);
        var url = source + "/" + fileName;

        if (url.startsWith("file://")) {
            var srcPath = Path.of(URI.create(url));
            Files.copy(srcPath, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } else {
            downloadRaw(url, target);
        }
        return target;
    }

    public Path retrieveFile(String fileName) throws CvmfsException {
        var cached = cache.get(fileName);
        if (cached != null) return cached;

        var target = cache.add(fileName);
        var url = source + "/" + fileName;
        try {
            downloadAndDecompress(url, target);
        } catch (IOException e) {
            throw new CvmfsException("File not found in repository: " + fileName, e);
        }
        var result = cache.get(fileName);
        if (result == null) throw new CvmfsException("File not found after download: " + fileName);
        return result;
    }

    private void downloadRaw(String url, Path target) throws IOException {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();
        try {
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofFile(target));
            if (response.statusCode() >= 400) {
                throw new IOException("HTTP " + response.statusCode() + " for " + url);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted", e);
        }
    }

    public Path retrieveFileVerified(String fileName, String expectedHash) throws CvmfsException {
        var path = retrieveFile(fileName);
        try {
            var data = Files.readAllBytes(path);
            verifyHash(data, expectedHash);
        } catch (IOException e) {
            throw new CvmfsException("Failed to read file for verification: " + fileName, e);
        }
        return path;
    }

    public static void verifyHash(byte[] data, String expectedHash) throws CvmfsException {
        var cleanHash = expectedHash.contains("-")
                ? expectedHash.substring(0, expectedHash.indexOf('-'))
                : expectedHash;
        try {
            var md = MessageDigest.getInstance("SHA-1");
            var computed = HexFormat.of().formatHex(md.digest(data));
            if (!computed.equals(cleanHash)) {
                throw new CvmfsException("Hash mismatch: expected " + cleanHash + ", got " + computed);
            }
        } catch (NoSuchAlgorithmException e) {
            throw new CvmfsException("SHA-1 not available", e);
        }
    }

    private void downloadAndDecompress(String url, Path target) throws IOException {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();
        try {
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                throw new IOException("HTTP " + response.statusCode() + " for " + url);
            }
            try (var in = new InflaterInputStream(response.body());
                 var out = Files.newOutputStream(target)) {
                in.transferTo(out);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted", e);
        }
    }
}
