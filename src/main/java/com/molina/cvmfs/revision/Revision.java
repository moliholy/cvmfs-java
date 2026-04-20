package com.molina.cvmfs.revision;

import com.molina.cvmfs.catalog.Catalog;
import com.molina.cvmfs.common.CvmfsException;
import com.molina.cvmfs.directoryentry.DirectoryEntry;
import com.molina.cvmfs.directoryentry.DirectoryEntryWrapper;
import com.molina.cvmfs.history.RevisionTag;
import com.molina.cvmfs.repository.Repository;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;

public class Revision {
    private final Repository repository;
    private final RevisionTag tag;

    public Revision(Repository repository, RevisionTag tag) {
        this.repository = repository;
        this.tag = tag;
    }

    public Repository repository() { return repository; }
    public RevisionTag tag() { return tag; }
    public int revisionNumber() { return tag.revision(); }
    public String name() { return tag.name(); }
    public long timestamp() { return tag.timestamp(); }
    public String rootHash() { return tag.hash(); }

    public Catalog retrieveRootCatalog() throws CvmfsException {
        return repository.retrieveCatalog(rootHash());
    }

    public Catalog retrieveCatalogForPath(String path) throws CvmfsException {
        return repository.retrieveCatalogForPath(path);
    }

    public DirectoryEntry lookup(String path) throws CvmfsException {
        return repository.lookup(path);
    }

    public Path getFile(String path) throws CvmfsException {
        return repository.getFile(path);
    }

    public List<DirectoryEntry> listDirectory(String path) throws CvmfsException {
        return repository.listDirectory(path);
    }

    public Iterator<DirectoryEntryWrapper> iterator() {
        return new RevisionIterator(this);
    }
}
