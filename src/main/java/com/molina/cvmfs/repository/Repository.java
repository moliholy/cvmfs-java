package com.molina.cvmfs.repository;

import com.molina.cvmfs.catalog.Catalog;
import com.molina.cvmfs.catalog.CatalogStatistics;
import com.molina.cvmfs.common.ChunkedFile;
import com.molina.cvmfs.common.CvmfsException;
import com.molina.cvmfs.common.FileLike;
import com.molina.cvmfs.common.RegularFile;
import com.molina.cvmfs.directoryentry.DirectoryEntry;
import com.molina.cvmfs.fetcher.Fetcher;
import com.molina.cvmfs.history.History;
import com.molina.cvmfs.history.RevisionTag;
import com.molina.cvmfs.manifest.Manifest;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class Repository {
    private final Map<String, Catalog> openedCatalogs = new HashMap<>();
    private final Manifest manifest;
    private final String fqrn;
    private final Fetcher fetcher;
    private RevisionTag currentTag;

    public Repository(Fetcher fetcher) throws IOException, CvmfsException {
        this.fetcher = fetcher;
        var manifestFile = fetcher.retrieveRawFile(com.molina.cvmfs.common.Common.MANIFEST_NAME);
        this.manifest = new Manifest(manifestFile.toFile());
        this.fqrn = manifest.repositoryName();
    }

    public Manifest manifest() { return manifest; }
    public String fqrn() { return fqrn; }
    public Fetcher fetcher() { return fetcher; }
    public Map<String, Catalog> openedCatalogs() { return Collections.unmodifiableMap(openedCatalogs); }

    public boolean hasHistory() { return manifest.hasHistory(); }

    public int getRevisionNumber() { return manifest.revision(); }
    public String getRootHash() { return manifest.rootHash(); }
    public String getName() { return manifest.repositoryName(); }
    public long getTimestamp() { return manifest.lastModified().getEpochSecond(); }

    public RevisionTag currentTag() throws CvmfsException {
        if (currentTag != null) return currentTag;
        try {
            var history = retrieveHistory();
            return history.getTagByRevision(manifest.revision())
                    .orElseThrow(() -> new CvmfsException("Current revision tag not found"));
        } catch (SQLException e) {
            throw new CvmfsException("Failed to get current tag", e);
        }
    }

    public void setCurrentTag(int revision) throws CvmfsException {
        try {
            var history = retrieveHistory();
            currentTag = history.getTagByRevision(revision)
                    .orElseThrow(() -> new CvmfsException("Revision " + revision + " not found"));
        } catch (SQLException e) {
            throw new CvmfsException("Failed to set tag", e);
        }
    }

    public RevisionTag getTag(int revision) throws CvmfsException {
        try {
            var history = retrieveHistory();
            return history.getTagByRevision(revision)
                    .orElseThrow(() -> new CvmfsException("Tag not found for revision " + revision));
        } catch (SQLException e) {
            throw new CvmfsException("Failed to get tag", e);
        }
    }

    public RevisionTag getLastTag() throws CvmfsException {
        try {
            var history = retrieveHistory();
            var tags = history.listTags();
            if (tags.isEmpty()) throw new CvmfsException("No tags found");
            return tags.getFirst();
        } catch (SQLException e) {
            throw new CvmfsException("Failed to get last tag", e);
        }
    }

    public boolean unloadCatalogs() {
        openedCatalogs.values().forEach(Catalog::close);
        openedCatalogs.clear();
        return true;
    }

    public Path retrieveObject(String objectHash, String suffix) throws CvmfsException {
        var path = com.molina.cvmfs.common.Common.composeObjectPath(objectHash, suffix);
        return fetcher.retrieveFile(path);
    }

    public Path retrieveObject(String objectHash) throws CvmfsException {
        return retrieveObject(objectHash, "");
    }

    public Catalog retrieveCatalog(String catalogHash) throws CvmfsException {
        if (openedCatalogs.containsKey(catalogHash)) {
            return openedCatalogs.get(catalogHash);
        }
        try {
            var file = retrieveObject(catalogHash, Catalog.CATALOG_ROOT_PREFIX);
            var catalog = new Catalog(file.toFile(), catalogHash);
            openedCatalogs.put(catalogHash, catalog);
            return catalog;
        } catch (SQLException e) {
            throw new CvmfsException("Failed to open catalog: " + catalogHash, e);
        }
    }

    public Catalog retrieveCurrentRootCatalog() throws CvmfsException {
        var hash = currentTag != null ? currentTag.hash() : manifest.rootHash();
        return retrieveCatalog(hash);
    }

    public Catalog retrieveCatalogForPath(String path) throws CvmfsException {
        var catalog = retrieveCurrentRootCatalog();
        try {
            while (true) {
                var nested = catalog.findNestedForPath(path);
                if (nested.isEmpty()) break;
                catalog = retrieveCatalog(nested.get().catalogHash());
            }
        } catch (SQLException e) {
            throw new CvmfsException("Failed to navigate catalogs", e);
        }
        return catalog;
    }

    public History retrieveHistory() throws CvmfsException {
        if (!hasHistory()) throw new CvmfsException("Repository has no history");
        try {
            var file = retrieveObject(manifest.historyDatabase(), "H");
            return new History(file.toFile());
        } catch (SQLException e) {
            throw new CvmfsException("Failed to open history database", e);
        }
    }

    public CatalogStatistics getStatistics() throws CvmfsException {
        try {
            var catalog = retrieveCurrentRootCatalog();
            return catalog.getStatistics();
        } catch (SQLException e) {
            throw new CvmfsException("Failed to get statistics", e);
        }
    }

    public List<DirectoryEntry> listDirectory(String path) throws CvmfsException {
        var entry = lookup(path);
        if (!entry.isDirectory()) throw new CvmfsException("Not a directory: " + path);
        try {
            var catalog = retrieveCatalogForPath(path);
            return catalog.listDirectory(path);
        } catch (SQLException e) {
            throw new CvmfsException("Failed to list directory: " + path, e);
        }
    }

    public DirectoryEntry lookup(String path) throws CvmfsException {
        var lookupPath = "/".equals(path) ? "" : path;
        try {
            var catalog = retrieveCatalogForPath(lookupPath);
            return catalog.findDirectoryEntry(lookupPath)
                    .orElseThrow(() -> new CvmfsException("Path not found: " + path));
        } catch (SQLException e) {
            throw new CvmfsException("Failed to lookup: " + path, e);
        }
    }

    public Path getFile(String path) throws CvmfsException {
        var entry = lookup(path);
        if (!entry.isFile()) throw new CvmfsException("Not a file: " + path);
        var hash = entry.contentHashString()
                .orElseThrow(() -> new CvmfsException("No content hash for: " + path));
        return retrieveObject(hash);
    }

    public FileLike openFile(String path) throws CvmfsException {
        var entry = lookup(path);
        if (!entry.isFile()) throw new CvmfsException("Not a file: " + path);

        try {
            if (entry.hasChunks()) {
                return new ChunkedFile(entry.chunks(), entry.size(), fetcher);
            }
            var hash = entry.contentHashString()
                    .orElseThrow(() -> new CvmfsException("No content hash for: " + path));
            var filePath = retrieveObject(hash);
            return new RegularFile(filePath);
        } catch (IOException e) {
            throw new CvmfsException("Failed to open file: " + path, e);
        }
    }
}
