package com.molina.cvmfs.catalog;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CatalogReferenceTest {

    @Test
    void recordFields() {
        var ref = new CatalogReference("/nested/path", "abc123", 1024);
        assertEquals("/nested/path", ref.rootPath());
        assertEquals("abc123", ref.catalogHash());
        assertEquals(1024, ref.catalogSize());
    }

    @Test
    void equalityByValue() {
        var a = new CatalogReference("/path", "hash", 100);
        var b = new CatalogReference("/path", "hash", 100);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void notEqualDifferentPath() {
        var a = new CatalogReference("/a", "hash", 100);
        var b = new CatalogReference("/b", "hash", 100);
        assertNotEquals(a, b);
    }

    @Test
    void zeroSize() {
        var ref = new CatalogReference("/path", "hash", 0);
        assertEquals(0, ref.catalogSize());
    }
}
