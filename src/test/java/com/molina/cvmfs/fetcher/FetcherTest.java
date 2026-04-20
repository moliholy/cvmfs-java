package com.molina.cvmfs.fetcher;

import com.molina.cvmfs.common.CvmfsException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

class FetcherTest {

    @Test
    void verifyHashMatchingSha1() throws Exception {
        var data = "hello world".getBytes(StandardCharsets.UTF_8);
        var md = MessageDigest.getInstance("SHA-1");
        var hash = HexFormat.of().formatHex(md.digest(data));
        assertDoesNotThrow(() -> Fetcher.verifyHash(data, hash));
    }

    @Test
    void verifyHashWithSuffix() throws Exception {
        var data = "test data".getBytes(StandardCharsets.UTF_8);
        var md = MessageDigest.getInstance("SHA-1");
        var hash = HexFormat.of().formatHex(md.digest(data)) + "-rmd160";
        assertDoesNotThrow(() -> Fetcher.verifyHash(data, hash));
    }

    @Test
    void verifyHashMismatch() {
        var data = "hello".getBytes(StandardCharsets.UTF_8);
        assertThrows(CvmfsException.class, () ->
                Fetcher.verifyHash(data, "0000000000000000000000000000000000000000"));
    }
}
