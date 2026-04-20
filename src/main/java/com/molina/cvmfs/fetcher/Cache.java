package com.molina.cvmfs.fetcher;

import com.molina.cvmfs.common.CvmfsException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Cache {
    static final Duration DEFAULT_TTL = Duration.ofHours(1);
    static final Duration DEFAULT_NEGATIVE_TTL = Duration.ofSeconds(5);
    static final long DEFAULT_QUOTA = 4L * 1024 * 1024 * 1024;

    private final Path cacheDir;
    private Duration ttl;
    private Duration negativeTtl;
    private long quota;
    private final Map<String, Instant> negativeEntries = new ConcurrentHashMap<>();

    public Cache(String cacheDirectoryPath) throws CvmfsException, IOException {
        this.cacheDir = Path.of(cacheDirectoryPath).toAbsolutePath();
        this.ttl = DEFAULT_TTL;
        this.negativeTtl = DEFAULT_NEGATIVE_TTL;
        this.quota = DEFAULT_QUOTA;
        if (!Files.isDirectory(cacheDir)) {
            throw new CvmfsException("Cache directory not found: " + cacheDir);
        }
        createCacheStructure();
    }

    public Cache withTtl(Duration ttl, Duration negativeTtl) {
        this.ttl = ttl;
        this.negativeTtl = negativeTtl;
        return this;
    }

    public Cache withQuota(long quotaBytes) {
        this.quota = quotaBytes;
        return this;
    }

    private void createCacheStructure() throws IOException {
        var dataDir = cacheDir.resolve("data");
        Files.createDirectories(dataDir);
        for (int i = 0x00; i <= 0xff; i++) {
            var subDir = dataDir.resolve(String.format("%02x", i));
            Files.createDirectories(subDir);
        }
    }

    public Path add(String fileName) {
        if (fileName.contains("..") || fileName.startsWith("/")) {
            throw new IllegalArgumentException("Invalid cache file name: " + fileName);
        }
        return cacheDir.resolve(fileName);
    }

    public Path get(String fileName) {
        if (isNegativeCached(fileName)) return null;

        var path = cacheDir.resolve(fileName);
        if (!Files.isRegularFile(path)) return null;

        if (!fileName.startsWith("data/") && isExpired(path)) {
            try { Files.deleteIfExists(path); } catch (IOException ignored) {}
            return null;
        }
        return path;
    }

    public void recordNegative(String fileName) {
        negativeEntries.put(fileName, Instant.now());
    }

    boolean isNegativeCached(String fileName) {
        var recorded = negativeEntries.get(fileName);
        if (recorded == null) return false;
        if (Duration.between(recorded, Instant.now()).compareTo(negativeTtl) < 0) {
            return true;
        }
        negativeEntries.remove(fileName);
        return false;
    }

    boolean isExpired(Path path) {
        try {
            var attrs = Files.readAttributes(path, BasicFileAttributes.class);
            var modified = attrs.lastModifiedTime().toInstant();
            return Duration.between(modified, Instant.now()).compareTo(ttl) > 0;
        } catch (IOException e) {
            return true;
        }
    }

    public long cacheSize() {
        return dirSize(cacheDir.resolve("data"));
    }

    public void enforceQuota() throws IOException {
        var current = cacheSize();
        if (current <= quota) return;

        record FileEntry(Path path, Instant accessed) {}
        var files = new java.util.ArrayList<FileEntry>();

        try (var walk = Files.walk(cacheDir.resolve("data"))) {
            walk.filter(Files::isRegularFile).forEach(p -> {
                try {
                    var attrs = Files.readAttributes(p, BasicFileAttributes.class);
                    var atime = attrs.lastAccessTime().toInstant();
                    files.add(new FileEntry(p, atime));
                } catch (IOException ignored) {}
            });
        }

        files.sort(Comparator.comparing(FileEntry::accessed));

        var size = current;
        for (var entry : files) {
            if (size <= quota) break;
            try {
                size -= Files.size(entry.path());
                Files.deleteIfExists(entry.path());
            } catch (IOException ignored) {}
        }
    }

    private long dirSize(Path dir) {
        if (!Files.isDirectory(dir)) return 0;
        try (var walk = Files.walk(dir)) {
            return walk.filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try { return Files.size(p); } catch (IOException e) { return 0; }
                    })
                    .sum();
        } catch (IOException e) {
            return 0;
        }
    }

    public void evict() throws IOException {
        var dataDir = cacheDir.resolve("data");
        if (Files.isDirectory(dataDir)) {
            try (var walk = Files.walk(dataDir)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                        });
            }
            createCacheStructure();
        }
        negativeEntries.clear();
    }

    public Duration ttl() { return ttl; }
    public Duration negativeTtl() { return negativeTtl; }
    public long quota() { return quota; }
    Path cacheDir() { return cacheDir; }
}
