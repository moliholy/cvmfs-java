package com.molina.cvmfs.dns;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DnsDiscoveryAdditionalTest {

    @Test
    void extractDomainMultiDot() {
        var result = DnsDiscovery.extractDomain("repo.a.b.c.d");
        assertTrue(result.isPresent());
        assertEquals("a.b.c.d", result.get());
    }

    @Test
    void extractDomainSingleChar() {
        var result = DnsDiscovery.extractDomain("a.b");
        assertTrue(result.isPresent());
        assertEquals("b", result.get());
    }

    @Test
    void extractDomainEmpty() {
        assertTrue(DnsDiscovery.extractDomain("").isEmpty());
    }

    @Test
    void extractDomainDotAtStart() {
        var result = DnsDiscovery.extractDomain(".domain");
        assertTrue(result.isPresent());
        assertEquals("domain", result.get());
    }

    @Test
    void discoverServersForDomainReturnsResults() throws Exception {
        var results = DnsDiscovery.discoverServersForDomain("nonexistent.invalid.local");
        assertNotNull(results);
    }
}
