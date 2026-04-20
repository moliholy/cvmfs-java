package com.molina.cvmfs.dns;

import com.molina.cvmfs.common.CvmfsException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DnsDiscoveryTest {

    @Test
    void extractDomainFromFqrn() {
        assertEquals("cern.ch", DnsDiscovery.extractDomain("atlas.cern.ch").orElseThrow());
        assertEquals("cern.ch", DnsDiscovery.extractDomain("boss.cern.ch").orElseThrow());
    }

    @Test
    void extractDomainSubdomain() {
        assertEquals("sub.example.com", DnsDiscovery.extractDomain("repo.sub.example.com").orElseThrow());
    }

    @Test
    void extractDomainNoDot() {
        assertTrue(DnsDiscovery.extractDomain("nodot").isEmpty());
    }

    @Test
    void extractDomainTrailingDot() {
        assertTrue(DnsDiscovery.extractDomain("trailing.").isEmpty());
    }

    @Test
    void discoverServersInvalidFqrn() {
        assertThrows(CvmfsException.class, () -> DnsDiscovery.discoverServers("nodot"));
    }

    @Test
    void srvPrefix() {
        assertEquals("_cvmfs._tcp", DnsDiscovery.CVMFS_SRV_PREFIX);
    }
}
