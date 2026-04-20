package com.molina.cvmfs.catalog;

import com.molina.cvmfs.directoryentry.DirectoryEntry;
import com.molina.cvmfs.directoryentry.DirectoryEntryWrapper;

import java.sql.SQLException;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.NoSuchElementException;

public class CatalogIterator implements Iterator<DirectoryEntryWrapper> {
    private final Catalog catalog;
    private final Deque<DirectoryEntryWrapper> backlog = new LinkedList<>();

    public CatalogIterator(Catalog catalog) {
        this.catalog = catalog;
        var rootPath = catalog.isRoot() ? "" : catalog.rootPrefix();
        try {
            catalog.findDirectoryEntry(rootPath)
                    .ifPresent(entry -> backlog.addFirst(new DirectoryEntryWrapper(entry, rootPath)));
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize catalog iterator", e);
        }
    }

    public Catalog catalog() { return catalog; }

    @Override
    public boolean hasNext() { return !backlog.isEmpty(); }

    @Override
    public DirectoryEntryWrapper next() {
        if (!hasNext()) throw new NoSuchElementException();
        var wrapper = backlog.removeFirst();
        var entry = wrapper.directoryEntry();
        if (entry.isDirectory()) {
            try {
                var children = catalog.listDirectorySplitMd5(entry.md5path1(), entry.md5path2());
                for (var child : children) {
                    backlog.addFirst(new DirectoryEntryWrapper(child, wrapper.path() + "/" + child.name()));
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to list directory", e);
            }
        }
        return wrapper;
    }
}
