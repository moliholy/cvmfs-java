package com.molina.cvmfs.manifest;

import com.molina.cvmfs.certificate.Certificate;
import com.molina.cvmfs.common.CvmfsException;
import com.molina.cvmfs.rootfile.RootFile;

import java.io.File;
import java.io.IOException;
import java.time.Instant;

public class Manifest extends RootFile {
    private String rootCatalog;
    private String rootHash;
    private long rootCatalogSize;
    private String certificate;
    private String historyDatabase;
    private Instant lastModified;
    private int ttl;
    private int revision;
    private String repositoryName;
    private String microCatalog;
    private boolean garbageCollectable;
    private boolean allowsAlternativeName;

    public Manifest(File file) throws IOException, CvmfsException {
        super(file);
    }

    public Manifest(RootFile rootFile) throws CvmfsException {
        super(rootFile);
    }

    public boolean hasHistory() {
        return historyDatabase != null;
    }

    @Override
    protected void readLine(String line) {
        char key = line.charAt(0);
        var data = line.substring(1);
        switch (key) {
            case 'C' -> rootCatalog = data;
            case 'R' -> rootHash = data;
            case 'B' -> rootCatalogSize = Long.parseLong(data);
            case 'X' -> certificate = data;
            case 'H' -> historyDatabase = data;
            case 'T' -> lastModified = Instant.ofEpochSecond(Long.parseLong(data));
            case 'D' -> ttl = Integer.parseInt(data);
            case 'S' -> revision = Integer.parseInt(data);
            case 'N' -> repositoryName = data;
            case 'L', 'Y' -> microCatalog = data;
            case 'G' -> garbageCollectable = parseBoolean(data);
            case 'A' -> allowsAlternativeName = parseBoolean(data);
            default -> {} // ignore unknown fields
        }
    }

    static boolean parseBoolean(String value) {
        return "yes".equals(value);
    }

    @Override
    protected void checkValidity() throws CvmfsException {
        if (rootCatalog == null) throw new CvmfsException("Manifest lacks a root catalog entry");
        if (rootHash == null) throw new CvmfsException("Manifest lacks a root hash entry");
        if (ttl == 0) throw new CvmfsException("Manifest lacks a TTL entry");
        if (revision == 0) throw new CvmfsException("Manifest lacks a revision entry");
        if (repositoryName == null) throw new CvmfsException("Manifest lacks a repository name");
    }

    public boolean verifySignature(Certificate cert) {
        return cert.verify(signature, signatureChecksum);
    }

    public String rootCatalog() { return rootCatalog; }
    public String rootHash() { return rootHash; }
    public long rootCatalogSize() { return rootCatalogSize; }
    public String certificate() { return certificate; }
    public String historyDatabase() { return historyDatabase; }
    public Instant lastModified() { return lastModified != null ? lastModified : Instant.EPOCH; }
    public int ttl() { return ttl; }
    public int revision() { return revision; }
    public String repositoryName() { return repositoryName; }
    public String microCatalog() { return microCatalog != null ? microCatalog : ""; }
    public boolean garbageCollectable() { return garbageCollectable; }
    public boolean allowsAlternativeName() { return allowsAlternativeName; }
}
