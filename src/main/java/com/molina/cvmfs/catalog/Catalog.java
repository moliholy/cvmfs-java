package com.molina.cvmfs.catalog;

import com.molina.cvmfs.common.Common;
import com.molina.cvmfs.common.DatabaseObject;
import com.molina.cvmfs.common.PathHash;
import com.molina.cvmfs.directoryentry.DirectoryEntry;
import com.molina.cvmfs.directoryentry.DirectoryEntryWrapper;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;

public class Catalog extends DatabaseObject implements Iterable<DirectoryEntryWrapper> {

    public static final String CATALOG_ROOT_PREFIX = "C";

    private static final String LISTING_QUERY = "SELECT " + DirectoryEntry.catalogDatabaseFields() +
            " FROM catalog WHERE parent_1 = ? AND parent_2 = ? ORDER BY name ASC";
    private static final String NESTED_COUNT = "SELECT count(*) FROM nested_catalogs";
    private static final String READ_CHUNK = "SELECT " + com.molina.cvmfs.directoryentry.Chunk.catalogDatabaseFields() +
            " FROM chunks WHERE md5path_1 = ? AND md5path_2 = ? ORDER BY offset ASC";
    private static final String FIND_MD5_PATH = "SELECT " + DirectoryEntry.catalogDatabaseFields() +
            " FROM catalog WHERE md5path_1 = ? AND md5path_2 = ? LIMIT 1";

    private float schema;
    private float schemaRevision;
    private int revision;
    private final String hash;
    private long lastModified;
    private String rootPrefix;
    private String previousRevision;

    private final PreparedStatement listStmt;
    private final PreparedStatement nestedCountStmt;
    private final PreparedStatement readChunkStmt;
    private final PreparedStatement findMd5Stmt;
    private final PreparedStatement listNestedStmt;

    public Catalog(File databaseFile, String catalogHash) throws SQLException {
        super(databaseFile);
        this.hash = catalogHash;
        readProperties();
        if (rootPrefix == null) rootPrefix = "/";
        if (lastModified == 0) lastModified = 0;

        listStmt = createPreparedStatement(LISTING_QUERY);
        nestedCountStmt = createPreparedStatement(NESTED_COUNT);
        readChunkStmt = createPreparedStatement(READ_CHUNK);
        findMd5Stmt = createPreparedStatement(FIND_MD5_PATH);
        var nestedSql = (schema <= 1.2 && schemaRevision > 0)
                ? "SELECT path, sha1, size FROM nested_catalogs"
                : "SELECT path, sha1 FROM nested_catalogs";
        listNestedStmt = createPreparedStatement(nestedSql);
    }

    private void readProperties() throws SQLException {
        var props = readPropertiesTable();
        props.forEach((key, value) -> {
            switch (key) {
                case "revision" -> revision = Integer.parseInt(value);
                case "schema" -> schema = Float.parseFloat(value);
                case "schema_revision" -> schemaRevision = Float.parseFloat(value);
                case "last_modified" -> lastModified = Long.parseLong(value);
                case "previous_revision" -> previousRevision = value;
                case "root_prefix" -> rootPrefix = value;
                default -> {}
            }
        });
    }

    public float schema() { return schema; }
    public float schemaRevision() { return schemaRevision; }
    public int revision() { return revision; }
    public String hash() { return hash; }
    public long lastModified() { return lastModified; }
    public String rootPrefix() { return rootPrefix; }
    public String previousRevision() { return previousRevision; }

    public boolean isRoot() { return "/".equals(rootPrefix); }

    public boolean hasNested() throws SQLException { return nestedCount() > 0; }

    public int nestedCount() throws SQLException {
        try (var rs = nestedCountStmt.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public List<CatalogReference> listNested() throws SQLException {
        boolean newVersion = schema <= 1.2 && schemaRevision > 0;
        var result = new ArrayList<CatalogReference>();
        try (var rs = listNestedStmt.executeQuery()) {
            while (rs.next()) {
                var path = rs.getString(1);
                var sha1 = rs.getString(2);
                int size = newVersion ? rs.getInt(3) : 0;
                result.add(new CatalogReference(path, sha1, size));
            }
        }
        return result;
    }

    public CatalogStatistics getStatistics() throws SQLException {
        return new CatalogStatistics(this);
    }

    private boolean pathSanitized(String needlePath, String catalogPath) {
        return needlePath.length() == catalogPath.length() ||
                (needlePath.length() > catalogPath.length() &&
                        needlePath.charAt(catalogPath.length()) == '/');
    }

    public Optional<CatalogReference> findNestedForPath(String needlePath) throws SQLException {
        var refs = listNested();
        CatalogReference bestMatch = null;
        int bestScore = 0;
        var normalizedPath = Common.canonicalizePath(needlePath);
        for (var ref : refs) {
            if (normalizedPath.startsWith(ref.rootPath()) &&
                    ref.rootPath().length() > bestScore &&
                    pathSanitized(needlePath, ref.rootPath())) {
                bestScore = ref.rootPath().length();
                bestMatch = ref;
            }
        }
        return Optional.ofNullable(bestMatch);
    }

    public List<DirectoryEntry> listDirectory(String path) throws SQLException {
        var realPath = Common.canonicalizePath(path);
        if ("/".equals(realPath)) realPath = "";
        try {
            var md = MessageDigest.getInstance("MD5");
            var hash = Common.splitMd5(md.digest(realPath.getBytes(StandardCharsets.UTF_8)));
            return listDirectorySplitMd5(hash.hash1(), hash.hash2());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 not available", e);
        }
    }

    public List<DirectoryEntry> listDirectorySplitMd5(long parent1, long parent2) throws SQLException {
        listStmt.setLong(1, parent1);
        listStmt.setLong(2, parent2);
        var result = new ArrayList<DirectoryEntry>();
        try (var rs = listStmt.executeQuery()) {
            while (rs.next()) {
                var entry = new DirectoryEntry(rs);
                readChunks(entry);
                result.add(entry);
            }
        }
        return result;
    }

    private void readChunks(DirectoryEntry entry) throws SQLException {
        if (schema < 2.4) return;
        readChunkStmt.setLong(1, entry.md5path1());
        readChunkStmt.setLong(2, entry.md5path2());
        try (var rs = readChunkStmt.executeQuery()) {
            entry.addChunks(rs);
        }
    }

    public Optional<DirectoryEntry> findDirectoryEntry(String rootPath) throws SQLException {
        var realPath = Common.canonicalizePath(rootPath);
        try {
            var md = MessageDigest.getInstance("MD5");
            var pathHash = Common.splitMd5(md.digest(realPath.getBytes(StandardCharsets.UTF_8)));
            findMd5Stmt.setLong(1, pathHash.hash1());
            findMd5Stmt.setLong(2, pathHash.hash2());
            try (var rs = findMd5Stmt.executeQuery()) {
                if (rs.next()) {
                    var entry = new DirectoryEntry(rs);
                    readChunks(entry);
                    return Optional.of(entry);
                }
            }
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 not available", e);
        }
        return Optional.empty();
    }

    @Override
    public Iterator<DirectoryEntryWrapper> iterator() {
        return new CatalogIterator(this);
    }

    @Override
    public void close() {
        try { listStmt.close(); } catch (SQLException ignored) {}
        try { nestedCountStmt.close(); } catch (SQLException ignored) {}
        try { readChunkStmt.close(); } catch (SQLException ignored) {}
        try { findMd5Stmt.close(); } catch (SQLException ignored) {}
        try { listNestedStmt.close(); } catch (SQLException ignored) {}
        super.close();
    }
}
