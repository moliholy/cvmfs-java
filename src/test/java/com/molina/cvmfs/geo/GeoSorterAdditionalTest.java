package com.molina.cvmfs.geo;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GeoSorterAdditionalTest {

    @Test
    void extractHostnameFileScheme() {
        assertEquals("", GeoSorter.extractHostname("file://"));
    }

    @Test
    void extractHostnameBare() {
        assertEquals("host", GeoSorter.extractHostname("host"));
    }

    @Test
    void parseGeoResponsePartial() {
        var indices = GeoSorter.parseGeoResponse("2,1", 5);
        assertEquals(List.of(1, 0), indices);
    }

    @Test
    void parseGeoResponseWithSpaces() {
        var indices = GeoSorter.parseGeoResponse(" 1 , 2 , 3 ", 3);
        assertEquals(List.of(0, 1, 2), indices);
    }

    @Test
    void parseGeoResponseMultiline() {
        var indices = GeoSorter.parseGeoResponse("2,1\nignored", 3);
        assertEquals(List.of(1, 0), indices);
    }

    @Test
    void parseGeoResponseDuplicateIndices() {
        var indices = GeoSorter.parseGeoResponse("1,1,2", 3);
        assertEquals(List.of(0, 0, 1), indices);
    }

    @Test
    void sortServersReturnsNewListForEmpty() {
        var servers = List.<String>of();
        var result = GeoSorter.sortServersByGeo("http://geo.api", "repo", servers);
        assertNotSame(servers, result);
    }

    @Test
    void sortServersReturnsCopyForSingle() {
        var servers = List.of("http://a.com");
        var result = GeoSorter.sortServersByGeo("http://geo.api", "repo", servers);
        assertEquals(servers, result);
        assertNotSame(servers, result);
    }

    @Test
    void sortServersInvalidGeoApiFallsBack() {
        var servers = List.of("http://a.com", "http://b.com");
        var result = GeoSorter.sortServersByGeo("http://invalid.local:1", "repo", servers);
        assertEquals(2, result.size());
    }

    @Test
    void geoApiPath() {
        assertEquals("api/v1.0/geo", GeoSorter.GEO_API_PATH);
    }

    @Test
    void geoTimeout() {
        assertEquals(5, GeoSorter.GEO_TIMEOUT.getSeconds());
    }

    @Test
    void parseGeoResponseZeroIndexIgnored() {
        var indices = GeoSorter.parseGeoResponse("0,1,2", 3);
        assertEquals(List.of(0, 1), indices);
    }
}
