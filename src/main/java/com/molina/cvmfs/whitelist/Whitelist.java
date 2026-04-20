package com.molina.cvmfs.whitelist;

import com.molina.cvmfs.common.CvmfsException;
import com.molina.cvmfs.rootfile.RootFile;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class Whitelist extends RootFile {
    private static final Pattern FINGERPRINT_PATTERN =
            Pattern.compile("^([0-9A-F]{2}:){19}[0-9A-F]{2}.*");
    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private List<String> fingerprints;
    private Instant expires;
    private String repositoryName;
    private Instant created;

    public Whitelist(File file) throws IOException, CvmfsException {
        super(file);
    }

    @Override
    protected void readLine(String line) {
        if (fingerprints == null) fingerprints = new ArrayList<>();
        if (FINGERPRINT_PATTERN.matcher(line).matches()) {
            fingerprints.add(line);
        } else if (line.charAt(0) == '2') {
            created = parseTimestamp(line);
        } else if (line.charAt(0) == 'E') {
            expires = parseTimestamp(line.substring(1));
        } else if (line.charAt(0) == 'N') {
            repositoryName = line.substring(1);
        }
    }

    private Instant parseTimestamp(String s) {
        var dt = LocalDateTime.parse(s, TIMESTAMP_FMT);
        return dt.toInstant(ZoneOffset.UTC);
    }

    @Override
    protected void checkValidity() throws CvmfsException {
        if (created == null) throw new CvmfsException("Whitelist without a timestamp");
        if (expires == null) throw new CvmfsException("Whitelist without expiry date");
        if (repositoryName == null) throw new CvmfsException("Whitelist without repository name");
    }

    public boolean isExpired() { return Instant.now().isAfter(expires); }
    public boolean matchesRepository(String fqrn) { return repositoryName.equals(fqrn); }

    public List<String> fingerprints() { return fingerprints; }
    public Instant expires() { return expires; }
    public Instant created() { return created; }
    public String repositoryName() { return repositoryName; }
}
