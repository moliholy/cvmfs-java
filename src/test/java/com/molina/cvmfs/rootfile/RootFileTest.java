package com.molina.cvmfs.rootfile;

import com.molina.cvmfs.common.CvmfsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class RootFileTest {

    @TempDir
    Path tempDir;

    static class TestRootFile extends RootFile {
        String capturedKey;
        String capturedValue;
        int lineCount;

        TestRootFile(File file) throws IOException, CvmfsException {
            super(file);
        }

        TestRootFile(RootFile other) throws CvmfsException {
            super(other);
        }

        @Override
        protected void readLine(String line) {
            lineCount++;
            if (line.contains("=")) {
                var parts = line.split("=", 2);
                capturedKey = parts[0];
                capturedValue = parts[1];
            }
        }

        @Override
        protected void checkValidity() {}
    }

    private File writeFile(String content) throws Exception {
        var file = tempDir.resolve("rootfile").toFile();
        Files.writeString(file.toPath(), content);
        return file;
    }

    @Test
    void parsesContentLines() throws Exception {
        var file = writeFile("key1=value1\nkey2=value2\n");
        var rf = new TestRootFile(file);
        assertEquals(2, rf.lineCount);
    }

    @Test
    void skipsEmptyLines() throws Exception {
        var file = writeFile("key=val\n\nother=line\n");
        var rf = new TestRootFile(file);
        assertEquals(2, rf.lineCount);
    }

    @Test
    void noSignature() throws Exception {
        var file = writeFile("line1\nline2\n");
        var rf = new TestRootFile(file);
        assertFalse(rf.hasSignature);
    }

    @Test
    void signatureWithValidChecksum() throws Exception {
        var content = "key=value\n";
        var md = MessageDigest.getInstance("SHA-1");
        var hash = HexFormat.of().formatHex(
                md.digest(content.getBytes(StandardCharsets.ISO_8859_1)));
        var full = content + "--\n" + hash + "\nsignaturedata\n";
        var file = writeFile(full);
        var rf = new TestRootFile(file);
        assertTrue(rf.hasSignature);
        assertEquals(hash, rf.signatureChecksum);
        assertNotNull(rf.signature);
    }

    @Test
    void signatureWithBadChecksum() throws Exception {
        var full = "key=value\n--\n" + "a".repeat(40) + "\nsigdata\n";
        var file = writeFile(full);
        var rf = new TestRootFile(file);
        assertFalse(rf.hasSignature);
    }

    @Test
    void signatureChecksumTooShort() throws Exception {
        var full = "key=value\n--\nshort\n";
        var file = writeFile(full);
        var rf = new TestRootFile(file);
        assertFalse(rf.hasSignature);
    }

    @Test
    void copyConstructor() throws Exception {
        var file = writeFile("key=value\nother=data\n");
        var original = new TestRootFile(file);
        var copy = new TestRootFile(original);
        assertEquals(2, copy.lineCount);
        assertEquals(original.hasSignature, copy.hasSignature);
    }

    @Test
    void contentLinesPreserved() throws Exception {
        var file = writeFile("line1\nline2\nline3\n");
        var rf = new TestRootFile(file);
        assertEquals(3, rf.getContentLines().size());
    }
}
