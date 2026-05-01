package com.molina.cvmfs.revision;

import com.molina.cvmfs.history.RevisionTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RevisionTest {

    @Test
    void accessors() {
        var tag = new RevisionTag("v1", "roothash", 42, 1700000000L, 0, "Release v1");
        var revision = new Revision(null, tag);
        assertNull(revision.repository());
        assertEquals(tag, revision.tag());
        assertEquals(42, revision.revisionNumber());
        assertEquals("v1", revision.name());
        assertEquals(1700000000L, revision.timestamp());
        assertEquals("roothash", revision.rootHash());
    }

    @Test
    void delegatesToTag() {
        var tag = new RevisionTag("release", "abc", 10, 500, 1, "desc");
        var revision = new Revision(null, tag);
        assertEquals(tag.revision(), revision.revisionNumber());
        assertEquals(tag.name(), revision.name());
        assertEquals(tag.timestamp(), revision.timestamp());
        assertEquals(tag.hash(), revision.rootHash());
    }
}
