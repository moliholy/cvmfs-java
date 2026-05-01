package com.molina.cvmfs.history;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RevisionTagTest {

    @Test
    void recordFields() {
        var tag = new RevisionTag("v1", "abc123", 5, 1700000000L, 1, "Release v1");
        assertEquals("v1", tag.name());
        assertEquals("abc123", tag.hash());
        assertEquals(5, tag.revision());
        assertEquals(1700000000L, tag.timestamp());
        assertEquals(1, tag.channel());
        assertEquals("Release v1", tag.description());
    }

    @Test
    void equalityByValue() {
        var a = new RevisionTag("v1", "h", 1, 100, 0, "d");
        var b = new RevisionTag("v1", "h", 1, 100, 0, "d");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void notEqualDifferentRevision() {
        var a = new RevisionTag("v1", "h", 1, 100, 0, "d");
        var b = new RevisionTag("v1", "h", 2, 100, 0, "d");
        assertNotEquals(a, b);
    }

    @Test
    void nullDescription() {
        var tag = new RevisionTag("v1", "h", 1, 100, 0, null);
        assertNull(tag.description());
    }
}
