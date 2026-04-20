package com.molina.cvmfs.reflog;

import com.molina.cvmfs.common.DatabaseObject;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class Reflog extends DatabaseObject {

    public Reflog(File databaseFile) throws SQLException {
        super(databaseFile);
    }

    public List<RefEntry> listRefs() throws SQLException {
        try (var stmt = createPreparedStatement(
                "SELECT hash, type, timestamp FROM refs ORDER BY timestamp DESC");
             var rs = stmt.executeQuery()) {
            return mapEntries(rs);
        }
    }

    public List<RefEntry> listRefsByType(RefType refType) throws SQLException {
        try (var stmt = createPreparedStatement(
                "SELECT hash, type, timestamp FROM refs WHERE type = ? ORDER BY timestamp DESC")) {
            stmt.setInt(1, refType.value());
            try (var rs = stmt.executeQuery()) {
                return mapEntries(rs);
            }
        }
    }

    public long countRefs() throws SQLException {
        try (var stmt = createPreparedStatement("SELECT COUNT(*) FROM refs");
             var rs = stmt.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    public boolean containsHash(String hash) throws SQLException {
        try (var stmt = createPreparedStatement("SELECT COUNT(*) FROM refs WHERE hash = ?")) {
            stmt.setString(1, hash);
            try (var rs = stmt.executeQuery()) {
                rs.next();
                return rs.getLong(1) > 0;
            }
        }
    }

    private List<RefEntry> mapEntries(java.sql.ResultSet rs) throws SQLException {
        var entries = new ArrayList<RefEntry>();
        while (rs.next()) {
            entries.add(new RefEntry(
                    rs.getString("hash"),
                    RefType.fromValue(rs.getInt("type")),
                    rs.getLong("timestamp")));
        }
        return entries;
    }
}
