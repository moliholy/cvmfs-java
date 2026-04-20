package com.molina.cvmfs.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class Common {

    public static final String REPO_CONFIG_PATH = "/etc/cvmfs/repositories.d";
    public static final String SERVER_CONFIG_NAME = "server.conf";
    public static final String REST_CONNECTOR = "control";
    public static final String WHITELIST_NAME = ".cvmfswhitelist";
    public static final String MANIFEST_NAME = ".cvmfspublished";
    public static final String LAST_REPLICATION_NAME = ".cvmfs_last_snapshot";
    public static final String REPLICATING_NAME = ".cvmfs_is_snapshotting";

    private static final HexFormat HEX = HexFormat.of();

    private Common() {}

    public static String toHex(byte[] bytes) {
        return HEX.formatHex(bytes);
    }

    public static String md5Hex(String input) {
        try {
            var md = MessageDigest.getInstance("MD5");
            return toHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 not available", e);
        }
    }

    public static String canonicalizePath(String path) {
        if (path == null || path.isEmpty()) return "";
        var normalized = java.nio.file.Path.of(path).normalize().toString();
        if (path.startsWith("/") && !normalized.startsWith("/")) {
            return "/" + normalized;
        }
        return normalized;
    }

    public static PathHash splitMd5(byte[] md5Digest) {
        long lo = 0;
        long hi = 0;
        for (int i = 0; i < 8; i++)
            lo |= ((long) (md5Digest[i] & 0xFF)) << (i * 8);
        for (int i = 8; i < 16; i++)
            hi |= ((long) (md5Digest[i] & 0xFF)) << ((i - 8) * 8);
        return new PathHash(lo, hi);
    }

    public static String composeObjectPath(String hash, String suffix) {
        return "data/" + hash.substring(0, 2) + "/" + hash.substring(2) + suffix;
    }
}
