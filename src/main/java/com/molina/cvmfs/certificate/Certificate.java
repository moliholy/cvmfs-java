package com.molina.cvmfs.certificate;

import com.molina.cvmfs.common.Common;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

public class Certificate {
    public static final String CERTIFICATE_ROOT_PREFIX = "X";

    private final X509Certificate x509;

    public Certificate(File certFile) throws CertificateException, IOException {
        var factory = CertificateFactory.getInstance("X.509");
        try (var in = new FileInputStream(certFile)) {
            x509 = (X509Certificate) factory.generateCertificate(in);
        }
    }

    public X509Certificate x509() { return x509; }

    public String fingerprint() {
        return fingerprint("SHA-1");
    }

    public String fingerprint(String algorithm) {
        try {
            var md = MessageDigest.getInstance(algorithm);
            return Common.toHex(md.digest(x509.getEncoded()));
        } catch (NoSuchAlgorithmException | java.security.cert.CertificateEncodingException e) {
            return null;
        }
    }

    public boolean verify(String signature, String message) {
        try {
            var pubkey = x509.getPublicKey();
            var sig = Signature.getInstance("SHA1withRSA");
            sig.initVerify(pubkey);
            sig.update(message.getBytes(StandardCharsets.UTF_8));
            return sig.verify(signature.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            return false;
        }
    }
}
