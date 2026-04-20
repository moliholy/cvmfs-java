package com.molina.cvmfs.rootfile;

import com.molina.cvmfs.common.CvmfsException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public abstract class RootFile {
    protected boolean hasSignature;
    protected String signatureChecksum;
    protected String signature;
    private final List<String> contentLines = new ArrayList<>();

    protected RootFile(File file) throws IOException, CvmfsException {
        try (var reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.ISO_8859_1))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) continue;
                if (line.startsWith("--")) {
                    hasSignature = true;
                    signatureChecksum = reader.readLine();
                    break;
                }
                contentLines.add(line);
                readLine(line);
            }
        }
        if (hasSignature && signatureChecksum != null && signatureChecksum.length() == 40) {
            verifyChecksum(file);
        } else {
            hasSignature = false;
        }
        checkValidity();
    }

    protected RootFile(RootFile other) throws CvmfsException {
        this.hasSignature = other.hasSignature;
        this.signatureChecksum = other.signatureChecksum;
        this.signature = other.signature;
        for (var line : other.contentLines) {
            if (!line.isEmpty()) {
                contentLines.add(line);
                readLine(line);
            }
        }
        checkValidity();
    }

    List<String> getContentLines() { return contentLines; }

    private void verifyChecksum(File file) throws IOException, CvmfsException {
        var raw = Files.readAllBytes(file.toPath());
        var content = new String(raw, StandardCharsets.ISO_8859_1);
        int separatorIdx = content.indexOf("\n--\n");
        if (separatorIdx < 0) {
            hasSignature = false;
            return;
        }

        var signedContent = content.substring(0, separatorIdx + 1);
        try {
            var md = MessageDigest.getInstance("SHA-1");
            var hash = HexFormat.of().formatHex(md.digest(signedContent.getBytes(StandardCharsets.ISO_8859_1)));
            if (!hash.equals(signatureChecksum)) {
                hasSignature = false;
            }
        } catch (NoSuchAlgorithmException e) {
            throw new CvmfsException("SHA-1 not available", e);
        }

        var afterSep = content.substring(separatorIdx + 4);
        int newline = afterSep.indexOf('\n');
        if (newline >= 0 && newline + 1 < afterSep.length()) {
            signature = afterSep.substring(newline + 1);
        }
    }

    protected abstract void readLine(String line) throws CvmfsException;
    protected abstract void checkValidity() throws CvmfsException;
}
