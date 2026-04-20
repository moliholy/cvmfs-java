package com.molina.cvmfs.geo;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GeoSorterTest {

    @Test
    void extractHostnameHttp() {
        assertEquals("example.com", GeoSorter.extractHostname("http://example.com/repo"));
    }

    @Test
    void extractHostnameHttps() {
        assertEquals("example.com", GeoSorter.extractHostname("https://example.com/path/to"));
    }

    @Test
    void extractHostnameNoScheme() {
        assertEquals("example.com", GeoSorter.extractHostname("example.com/repo"));
    }

    @Test
    void extractHostnameNoPath() {
        assertEquals("example.com", GeoSorter.extractHostname("http://example.com"));
    }

    @Test
    void parseGeoResponseValid() {
        var indices = GeoSorter.parseGeoResponse("3,1,2", 3);
        assertEquals(List.of(2, 0, 1), indices);
    }

    @Test
    void parseGeoResponseOutOfRange() {
        var indices = GeoSorter.parseGeoResponse("1,5,2", 3);
        assertEquals(List.of(0, 1), indices);
    }

    @Test
    void parseGeoResponseEmpty() {
        var indices = GeoSorter.parseGeoResponse("", 3);
        assertTrue(indices.isEmpty());
    }

    @Test
    void parseGeoResponseInvalid() {
        var indices = GeoSorter.parseGeoResponse("abc,def", 3);
        assertTrue(indices.isEmpty());
    }

    @Test
    void sortSingleServer() {
        var servers = List.of("http://only.one.com");
        var result = GeoSorter.sortServersByGeo("http://geo.api", "repo", servers);
        assertEquals(servers, result);
    }

    @Test
    void sortEmptyServers() {
        var result = GeoSorter.sortServersByGeo("http://geo.api", "repo", List.of());
        assertTrue(result.isEmpty());
    }
}
