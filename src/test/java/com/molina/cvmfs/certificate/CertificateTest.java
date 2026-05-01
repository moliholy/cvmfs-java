package com.molina.cvmfs.certificate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class CertificateTest {

    @TempDir
    Path tempDir;
    private File certFile;

    @BeforeEach
    void setUp() throws Exception {
        var keystore = tempDir.resolve("keystore.jks").toFile();
        new ProcessBuilder("keytool",
                "-genkeypair", "-alias", "test",
                "-keyalg", "RSA", "-keysize", "2048",
                "-dname", "CN=Test",
                "-validity", "365",
                "-storepass", "changeit",
                "-keypass", "changeit",
                "-keystore", keystore.getAbsolutePath())
                .redirectErrorStream(true).start().waitFor();

        var ks = KeyStore.getInstance("JKS");
        try (var fis = new FileInputStream(keystore)) {
            ks.load(fis, "changeit".toCharArray());
        }
        var cert = ks.getCertificate("test");

        certFile = tempDir.resolve("test.pem").toFile();
        var pem = "-----BEGIN CERTIFICATE-----\n" +
                Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(cert.getEncoded()) +
                "\n-----END CERTIFICATE-----\n";
        Files.writeString(certFile.toPath(), pem);
    }

    @Test
    void loadsX509Certificate() throws Exception {
        var certificate = new Certificate(certFile);
        assertNotNull(certificate.x509());
        assertInstanceOf(X509Certificate.class, certificate.x509());
    }

    @Test
    void fingerprintSha1() throws Exception {
        var certificate = new Certificate(certFile);
        var fp = certificate.fingerprint();
        assertNotNull(fp);
        assertEquals(40, fp.length());
    }

    @Test
    void fingerprintSha256() throws Exception {
        var certificate = new Certificate(certFile);
        var fp = certificate.fingerprint("SHA-256");
        assertNotNull(fp);
        assertEquals(64, fp.length());
    }

    @Test
    void fingerprintConsistent() throws Exception {
        var cert = new Certificate(certFile);
        assertEquals(cert.fingerprint(), cert.fingerprint());
    }

    @Test
    void verifyInvalidSignatureReturnsFalse() throws Exception {
        var cert = new Certificate(certFile);
        assertFalse(cert.verify("invalid", "test message"));
    }

    @Test
    void invalidFileThrows() throws Exception {
        var badFile = tempDir.resolve("bad.pem").toFile();
        Files.writeString(badFile.toPath(), "not a certificate");
        assertThrows(CertificateException.class, () -> new Certificate(badFile));
    }
}
