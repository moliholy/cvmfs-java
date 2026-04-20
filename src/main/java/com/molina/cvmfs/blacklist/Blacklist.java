package com.molina.cvmfs.blacklist;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Blacklist {
    static final String DEFAULT_BLACKLIST_PATH = "/etc/cvmfs/blacklist";

    private final List<String> fingerprints;
    private final List<RevisionBlock> revisionBlocks;

    record RevisionBlock(String repoName, int maxRevision) {}

    private Blacklist(List<String> fingerprints, List<RevisionBlock> revisionBlocks) {
        this.fingerprints = List.copyOf(fingerprints);
        this.revisionBlocks = List.copyOf(revisionBlocks);
    }

    public static Blacklist loadDefault() {
        try {
            return load(DEFAULT_BLACKLIST_PATH);
        } catch (IOException e) {
            return empty();
        }
    }

    public static Blacklist load(String path) throws IOException {
        var file = Path.of(path);
        if (!Files.exists(file)) return empty();
        return parse(Files.readString(file));
    }

    public static Blacklist parse(String content) {
        var fingerprints = new ArrayList<String>();
        var revisionBlocks = new ArrayList<RevisionBlock>();

        for (var line : content.split("\n")) {
            var trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

            if (trimmed.startsWith("<")) {
                var parts = trimmed.substring(1).strip().split("\\s+", 2);
                if (parts.length == 2) {
                    try {
                        int rev = Integer.parseInt(parts[1]);
                        revisionBlocks.add(new RevisionBlock(parts[0], rev));
                    } catch (NumberFormatException ignored) {}
                }
            } else {
                fingerprints.add(trimmed);
            }
        }
        return new Blacklist(fingerprints, revisionBlocks);
    }

    public static Blacklist empty() {
        return new Blacklist(List.of(), List.of());
    }

    public boolean isFingerprintBlocked(String fingerprint) {
        return fingerprints.contains(fingerprint);
    }

    public boolean isRevisionBlocked(String repoName, int revision) {
        return revisionBlocks.stream()
                .anyMatch(b -> b.repoName().equals(repoName) && revision < b.maxRevision());
    }

    public List<String> fingerprints() { return fingerprints; }
    public int revisionBlockCount() { return revisionBlocks.size(); }
}
