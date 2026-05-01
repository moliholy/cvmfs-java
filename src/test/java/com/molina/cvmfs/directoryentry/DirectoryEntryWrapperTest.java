package com.molina.cvmfs.directoryentry;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class DirectoryEntryWrapperTest {

    @Test
    void recordFields() {
        var entry = new DirectoryEntry(1, 2, 3, 4, "hash", Flags.FILE,
                100, 0644, 1000, "file.txt", null, 0, 0, 1, new ArrayList<>());
        var wrapper = new DirectoryEntryWrapper(entry, "/path/to/file.txt");
        assertSame(entry, wrapper.directoryEntry());
        assertEquals("/path/to/file.txt", wrapper.path());
    }

    @Test
    void equalityByValue() {
        var entry = new DirectoryEntry(1, 2, 3, 4, "hash", Flags.FILE,
                100, 0644, 1000, "file.txt", null, 0, 0, 1, new ArrayList<>());
        var a = new DirectoryEntryWrapper(entry, "/path");
        var b = new DirectoryEntryWrapper(entry, "/path");
        assertEquals(a, b);
    }
}
