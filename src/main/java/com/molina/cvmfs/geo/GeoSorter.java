package com.molina.cvmfs.geo;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class GeoSorter {
    static final String GEO_API_PATH = "api/v1.0/geo";
    static final Duration GEO_TIMEOUT = Duration.ofSeconds(5);

    private GeoSorter() {}

    public static List<String> sortServersByGeo(String geoApiServer, String repoName, List<String> servers) {
        if (servers.size() <= 1) return new ArrayList<>(servers);

        var hostnames = servers.stream()
                .map(GeoSorter::extractHostname)
                .collect(Collectors.joining(","));

        var url = geoApiServer + "/" + GEO_API_PATH + "/" + repoName + "/me/" + hostnames;

        try {
            var client = HttpClient.newBuilder()
                    .connectTimeout(GEO_TIMEOUT)
                    .build();
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(GEO_TIMEOUT)
                    .GET()
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) return new ArrayList<>(servers);

            var indices = parseGeoResponse(response.body(), servers.size());
            var sorted = new ArrayList<String>(servers.size());
            var used = new boolean[servers.size()];
            for (var idx : indices) {
                sorted.add(servers.get(idx));
                used[idx] = true;
            }
            for (int i = 0; i < servers.size(); i++) {
                if (!used[i]) sorted.add(servers.get(i));
            }
            return sorted;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return new ArrayList<>(servers);
        }
    }

    static String extractHostname(String url) {
        var s = url;
        for (var prefix : List.of("https://", "http://", "file://")) {
            if (s.startsWith(prefix)) {
                s = s.substring(prefix.length());
                break;
            }
        }
        int slash = s.indexOf('/');
        return slash >= 0 ? s.substring(0, slash) : s;
    }

    static List<Integer> parseGeoResponse(String body, int serverCount) {
        var line = body.lines().findFirst().orElse("");
        var indices = new ArrayList<Integer>();
        for (var part : line.split(",")) {
            try {
                int idx = Integer.parseInt(part.strip());
                if (idx >= 1 && idx <= serverCount) {
                    indices.add(idx - 1);
                }
            } catch (NumberFormatException ignored) {}
        }
        return indices;
    }
}
