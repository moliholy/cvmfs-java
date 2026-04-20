package com.molina.cvmfs.revision;

import com.molina.cvmfs.catalog.CatalogIterator;
import com.molina.cvmfs.common.CvmfsException;
import com.molina.cvmfs.directoryentry.DirectoryEntryWrapper;

import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.NoSuchElementException;

public class RevisionIterator implements Iterator<DirectoryEntryWrapper> {
    private final Revision revision;
    private final Deque<CatalogIterator> catalogStack = new LinkedList<>();

    public RevisionIterator(Revision revision) {
        this.revision = revision;
        try {
            var rootCatalog = revision.retrieveRootCatalog();
            catalogStack.addLast(new CatalogIterator(rootCatalog));
        } catch (CvmfsException e) {
            throw new RuntimeException("Failed to initialize revision iterator", e);
        }
    }

    @Override
    public boolean hasNext() { return !catalogStack.isEmpty(); }

    @Override
    public DirectoryEntryWrapper next() {
        if (!hasNext()) throw new NoSuchElementException();
        var current = catalogStack.peekFirst();
        if (current == null || !current.hasNext()) {
            catalogStack.removeFirst();
            return hasNext() ? next() : null;
        }
        var wrapper = current.next();
        if (wrapper.directoryEntry().isNestedCatalogMountpoint()) {
            try {
                var catalog = revision.retrieveCatalogForPath(wrapper.path());
                catalogStack.addLast(new CatalogIterator(catalog));
            } catch (CvmfsException e) {
                throw new RuntimeException("Failed to load nested catalog", e);
            }
        }
        if (!current.hasNext()) {
            catalogStack.removeFirst();
        }
        return wrapper;
    }
}
