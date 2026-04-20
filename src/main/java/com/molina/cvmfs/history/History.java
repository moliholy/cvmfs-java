package com.molina.cvmfs.history;

import com.molina.cvmfs.common.DatabaseObject;

import java.io.File;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class History extends DatabaseObject {
    private static final String FIELDS = "name, hash, revision, timestamp, channel, description";

    private final String schema;
    private final String fqrn;
    private final PreparedStatement byNameStmt;
    private final PreparedStatement byRevisionStmt;
    private final PreparedStatement byDateStmt;

    public History(File databaseFile) throws SQLException {
        super(databaseFile);
        var props = readPropertiesTable();
        schema = props.getOrDefault("schema", "1.0");
        fqrn = props.get("fqrn");

        byNameStmt = createPreparedStatement(
                "SELECT " + FIELDS + " FROM tags WHERE name = ? LIMIT 1");
        byRevisionStmt = createPreparedStatement(
                "SELECT " + FIELDS + " FROM tags WHERE revision = ? LIMIT 1");
        byDateStmt = createPreparedStatement(
                "SELECT " + FIELDS + " FROM tags WHERE timestamp > ? ORDER BY timestamp ASC LIMIT 1");
    }

    public String schema() { return schema; }
    public String fqrn() { return fqrn; }

    public List<RevisionTag> listTags() throws SQLException {
        var tags = new ArrayList<RevisionTag>();
        try (var stmt = createStatement();
             var rs = stmt.executeQuery("SELECT " + FIELDS + " FROM tags ORDER BY timestamp DESC")) {
            while (rs.next()) {
                tags.add(RevisionTag.fromResultSet(rs));
            }
        }
        return tags;
    }

    public Optional<RevisionTag> getTagByName(String name) throws SQLException {
        byNameStmt.setString(1, name);
        try (var rs = byNameStmt.executeQuery()) {
            return rs.next() ? Optional.of(RevisionTag.fromResultSet(rs)) : Optional.empty();
        }
    }

    public Optional<RevisionTag> getTagByRevision(int revision) throws SQLException {
        byRevisionStmt.setInt(1, revision);
        try (var rs = byRevisionStmt.executeQuery()) {
            return rs.next() ? Optional.of(RevisionTag.fromResultSet(rs)) : Optional.empty();
        }
    }

    public Optional<RevisionTag> getTagByDate(long timestamp) throws SQLException {
        byDateStmt.setLong(1, timestamp);
        try (var rs = byDateStmt.executeQuery()) {
            return rs.next() ? Optional.of(RevisionTag.fromResultSet(rs)) : Optional.empty();
        }
    }

    @Override
    public void close() {
        try { byNameStmt.close(); } catch (SQLException ignored) {}
        try { byRevisionStmt.close(); } catch (SQLException ignored) {}
        try { byDateStmt.close(); } catch (SQLException ignored) {}
        super.close();
    }
}
