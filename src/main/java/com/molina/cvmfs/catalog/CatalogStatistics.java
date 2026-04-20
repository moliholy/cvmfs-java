package com.molina.cvmfs.catalog;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class CatalogStatistics {
    private final Map<String, Long> stats;

    public CatalogStatistics(Catalog catalog) throws SQLException {
        this.stats = new HashMap<>();
        if (catalog.schema() >= 2.1) {
            readStatistics(catalog);
        }
    }

    private void readStatistics(Catalog catalog) throws SQLException {
        try (var stmt = catalog.createStatement();
             var rs = stmt.executeQuery("SELECT * FROM statistics ORDER BY counter")) {
            while (rs.next()) {
                var counter = rs.getString(1);
                long value = rs.getLong(2);
                if (counter.startsWith("self_")) {
                    stats.put(counter.substring(5), value);
                } else if (counter.startsWith("subtree_")) {
                    var key = counter.substring(8);
                    stats.put("all_" + key, value + stats.getOrDefault(key, 0L));
                }
            }
        }
    }

    public long dir() { return stat("dir"); }
    public long regular() { return stat("regular"); }
    public long symlink() { return stat("symlink"); }
    public long fileSize() { return stat("file_size"); }
    public long chunked() { return stat("chunked"); }
    public long chunks() { return stat("chunks"); }

    public long stat(String name) {
        return stats.getOrDefault(name, 0L);
    }
}
