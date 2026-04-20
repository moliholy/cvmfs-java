package com.molina.cvmfs.fetcher;

import com.molina.cvmfs.common.CvmfsException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public class Cache {
    private final Path cacheDir;

    public Cache(String cacheDirectoryPath) throws CvmfsException, IOException {
        this.cacheDir = Path.of(cacheDirectoryPath).toAbsolutePath();
        if (!Files.isDirectory(cacheDir)) {
            throw new CvmfsException("Cache directory not found: " + cacheDir);
        }
        createCacheStructure();
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
        return cacheDir.resolve(fileName);
    }

    public Path get(String fileName) {
        var path = cacheDir.resolve(fileName);
        return Files.isRegularFile(path) ? path : null;
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
    }
}
