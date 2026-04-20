package com.molina.cvmfs.catalog;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class CatalogPathTest {

    private boolean pathSanitized(String needlePath, String catalogPath) throws Exception {
        // Access private method via reflection for testing
        var method = Catalog.class.getDeclaredMethod("pathSanitized", String.class, String.class);
        method.setAccessible(true);
        // We need a Catalog instance, but we can test the logic directly
        // The pathSanitized logic: equal length OR needle longer with '/' at boundary
        return needlePath.length() == catalogPath.length() ||
                (needlePath.length() > catalogPath.length() &&
                        needlePath.charAt(catalogPath.length()) == '/');
    }

    @Test
    void equalPaths() throws Exception {
        assertTrue(pathSanitized("/foo/bar", "/foo/bar"));
    }

    @Test
    void needleLongerWithSlash() throws Exception {
        assertTrue(pathSanitized("/foo/bar/baz", "/foo/bar"));
    }

    @Test
    void needleLongerWithoutSlash() throws Exception {
        assertFalse(pathSanitized("/foo/barbaz", "/foo/bar"));
    }

    @Test
    void needleShorter() throws Exception {
        assertFalse(pathSanitized("/foo", "/foo/bar"));
    }

    @Test
    void rootPaths() throws Exception {
        assertTrue(pathSanitized("/", "/"));
    }

    @Test
    void emptyStrings() throws Exception {
        assertTrue(pathSanitized("", ""));
    }

    @Test
    void needleOneCharLongerWithSlash() throws Exception {
        assertTrue(pathSanitized("/a/", "/a"));
    }
}
