package com.molina.cvmfs.history;

import java.sql.ResultSet;
import java.sql.SQLException;

public record RevisionTag(
        String name,
        String hash,
        int revision,
        long timestamp,
        int channel,
        String description
) {
    public static RevisionTag fromResultSet(ResultSet rs) throws SQLException {
        return new RevisionTag(
                rs.getString(1),
                rs.getString(2),
                rs.getInt(3),
                rs.getLong(4),
                rs.getInt(5),
                rs.getString(6)
        );
    }
}
