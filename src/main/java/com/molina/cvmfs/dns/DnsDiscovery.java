package com.molina.cvmfs.dns;

import com.molina.cvmfs.common.CvmfsException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class DnsDiscovery {
    static final String CVMFS_SRV_PREFIX = "_cvmfs._tcp";

    private DnsDiscovery() {}

    public static List<String> discoverServers(String repoFqrn) throws CvmfsException {
        var domain = extractDomain(repoFqrn)
                .orElseThrow(() -> new CvmfsException("Cannot extract domain from FQRN: " + repoFqrn));
        return discoverServersForDomain(domain);
    }

    public static List<String> discoverServersForDomain(String domain) throws CvmfsException {
        return lookupTxtRecords(CVMFS_SRV_PREFIX + "." + domain);
    }

    static List<String> lookupTxtRecords(String queryName) throws CvmfsException {
        try {
            var process = new ProcessBuilder("dig", "+short", "TXT", queryName)
                    .redirectErrorStream(true)
                    .start();
            var output = new String(process.getInputStream().readAllBytes());
            int exit = process.waitFor();
            if (exit != 0) {
                throw new CvmfsException("dig command failed with exit code " + exit);
            }

            var results = new ArrayList<String>();
            for (var line : output.split("\n")) {
                var trimmed = line.strip().replace("\"", "");
                if (trimmed.isEmpty()) continue;
                for (var part : trimmed.split(";")) {
                    var entry = part.strip();
                    if (!entry.isEmpty()) results.add(entry);
                }
            }
            return results;
        } catch (IOException e) {
            throw new CvmfsException("Failed to execute dig command", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CvmfsException("DNS lookup interrupted", e);
        }
    }

    public static Optional<String> extractDomain(String fqrn) {
        int dot = fqrn.indexOf('.');
        if (dot < 0 || dot + 1 >= fqrn.length()) return Optional.empty();
        return Optional.of(fqrn.substring(dot + 1));
    }
}
