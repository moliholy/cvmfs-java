package com.molina.cvmfs.breadcrumb;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class Breadcrumb {
    private Breadcrumb() {}

    public static void write(String cacheDir, String fqrn, String catalogHash) throws IOException {
        Files.writeString(path(cacheDir, fqrn), catalogHash);
    }

    public static Optional<String> read(String cacheDir, String fqrn) {
        try {
            var content = Files.readString(path(cacheDir, fqrn)).strip();
            return content.isEmpty() ? Optional.empty() : Optional.of(content);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public static void remove(String cacheDir, String fqrn) {
        try {
            Files.deleteIfExists(path(cacheDir, fqrn));
        } catch (IOException ignored) {}
    }

    static Path path(String cacheDir, String fqrn) {
        return Path.of(cacheDir, "cvmfschecksum." + fqrn);
    }
}
