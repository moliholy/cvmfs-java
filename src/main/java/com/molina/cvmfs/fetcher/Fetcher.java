package com.molina.cvmfs.fetcher;

import com.molina.cvmfs.common.CvmfsException;

import com.github.luben.zstd.ZstdInputStream;
import net.jpountz.lz4.LZ4FrameInputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import java.util.zip.InflaterInputStream;

public class Fetcher {
    static final int MAX_RETRIES = 3;
    static final Duration BACKOFF_INIT = Duration.ofSeconds(2);
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private final Cache cache;
    private String source;
    private List<String> mirrors;
    private String proxy;
    private final AtomicBoolean offline = new AtomicBoolean(false);
    private final AtomicLong ioErrors = new AtomicLong(0);

    public Fetcher(String source, String cacheDirectory, boolean verifyHash) throws IOException, CvmfsException {
        this.cache = new Cache(cacheDirectory);
        this.mirrors = new ArrayList<>();

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

    public static Fetcher withMirrors(List<String> sources, String cacheDirectory) throws IOException, CvmfsException {
        if (sources.isEmpty()) throw new CvmfsException("At least one source is required");
        var fetcher = new Fetcher(sources.getFirst(), cacheDirectory);
        for (int i = 1; i < sources.size(); i++) {
            var s = sources.get(i);
            var p = Path.of(s);
            fetcher.mirrors.add(Files.isDirectory(p) ? "file://" + p.toAbsolutePath() : s);
        }
        return fetcher;
    }

    public void setProxy(String proxyUrl) { this.proxy = proxyUrl; }
    public String source() { return source; }
    public List<String> mirrors() { return List.copyOf(mirrors); }
    public boolean isOffline() { return offline.get(); }
    public long ioErrorCount() { return ioErrors.get(); }

    public void setSources(List<String> sorted) {
        if (sorted.isEmpty()) return;
        this.source = sorted.getFirst();
        this.mirrors = new ArrayList<>(sorted.subList(1, sorted.size()));
    }

    private Stream<String> allSources() {
        return Stream.concat(Stream.of(source), mirrors.stream());
    }

    public Path retrieveRawFile(String fileName) throws IOException {
        var target = cache.add(fileName);
        IOException lastError = null;

        for (var src : allSources().toList()) {
            var url = src + "/" + fileName;
            try {
                if (url.startsWith("file://")) {
                    var srcPath = Path.of(URI.create(url));
                    Files.copy(srcPath, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } else {
                    downloadRaw(url, target);
                }
                return target;
            } catch (IOException e) {
                lastError = e;
            }
        }
        throw lastError != null ? lastError : new IOException("No sources available for: " + fileName);
    }

    public Path retrieveFile(String fileName) throws CvmfsException {
        var cached = cache.get(fileName);
        if (cached != null) return cached;
        if (offline.get()) throw new CvmfsException("Fetcher is offline: " + fileName);

        try {
            var path = retrieveFileFromSources(fileName);
            offline.set(false);
            return path;
        } catch (CvmfsException e) {
            ioErrors.incrementAndGet();
            var stale = cache.add(fileName);
            if (Files.isRegularFile(stale)) return stale;
            throw e;
        }
    }

    private Path retrieveFileFromSources(String fileName) throws CvmfsException {
        var target = cache.add(fileName);
        CvmfsException lastError = null;

        for (var src : allSources().toList()) {
            var url = src + "/" + fileName;
            try {
                downloadAndDecompress(url, target);
                return target;
            } catch (IOException e) {
                lastError = new CvmfsException("Failed to retrieve from " + src + ": " + fileName, e);
            }
        }
        throw lastError != null ? lastError : new CvmfsException("No sources available for: " + fileName);
    }

    private void downloadRaw(String url, Path target) throws IOException {
        var bytes = downloadBytesWithRetry(url);
        Files.write(target, bytes);
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
        var compressed = downloadBytesWithRetry(url);
        var decompressed = decompress(compressed);
        Files.write(target, decompressed);
    }

    byte[] downloadBytesWithRetry(String url) throws IOException {
        var client = buildClient();
        IOException lastError = null;
        var backoff = BACKOFF_INIT;

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                var request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(REQUEST_TIMEOUT)
                        .GET()
                        .build();
                var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() >= 400) {
                    throw new IOException("HTTP " + response.statusCode() + " for " + url);
                }
                return response.body();
            } catch (IOException e) {
                lastError = e;
                if (attempt < MAX_RETRIES) {
                    try { Thread.sleep(backoff.toMillis()); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Download interrupted", ie);
                    }
                    backoff = backoff.multipliedBy(2);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Download interrupted", e);
            }
        }
        throw lastError;
    }

    private HttpClient buildClient() {
        var builder = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT);
        if (proxy != null) {
            var uri = URI.create(proxy);
            int port = uri.getPort() > 0 ? uri.getPort() : 80;
            builder.proxy(ProxySelector.of(new InetSocketAddress(uri.getHost(), port)));
        }
        return builder.build();
    }

    static byte[] decompress(byte[] data) throws IOException {
        try {
            return decompressZlib(data);
        } catch (IOException ignored) {}

        try {
            return decompressZstd(data);
        } catch (IOException ignored) {}

        try {
            return decompressLz4(data);
        } catch (IOException ignored) {}

        throw new IOException("Failed to decompress data with any supported format (zlib, zstd, lz4)");
    }

    static byte[] decompressZlib(byte[] data) throws IOException {
        try (var in = new InflaterInputStream(new ByteArrayInputStream(data));
             var out = new ByteArrayOutputStream()) {
            in.transferTo(out);
            return out.toByteArray();
        }
    }

    static byte[] decompressZstd(byte[] data) throws IOException {
        try (var in = new ZstdInputStream(new ByteArrayInputStream(data));
             var out = new ByteArrayOutputStream()) {
            in.transferTo(out);
            return out.toByteArray();
        }
    }

    static byte[] decompressLz4(byte[] data) throws IOException {
        try (var in = new LZ4FrameInputStream(new ByteArrayInputStream(data));
             var out = new ByteArrayOutputStream()) {
            in.transferTo(out);
            return out.toByteArray();
        }
    }
}
