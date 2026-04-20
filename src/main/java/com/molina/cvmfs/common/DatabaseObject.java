package com.molina.cvmfs.common;

import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteOpenMode;

import java.io.File;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

public class DatabaseObject implements AutoCloseable {
    protected final File databaseFile;
    private Connection connection;

    public DatabaseObject(File databaseFile) throws SQLException {
        this.databaseFile = databaseFile;
        if (this.databaseFile == null || !this.databaseFile.exists()) {
            throw new IllegalStateException("Database file is null or doesn't exist");
        }
        openDatabase();
    }

    protected void openDatabase() throws SQLException {
        var config = new SQLiteConfig();
        config.setReadOnly(true);
        config.setOpenMode(SQLiteOpenMode.FULLMUTEX);
        config.setLockingMode(SQLiteConfig.LockingMode.EXCLUSIVE);
        var url = "jdbc:sqlite:" + databaseFile.getAbsolutePath();
        connection = config.createConnection(url);
        connection.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
        connection.setAutoCommit(false);
    }

    protected PreparedStatement createPreparedStatement(String sql) throws SQLException {
        return connection.prepareStatement(sql);
    }

    public boolean isOpen() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {}
            connection = null;
        }
    }

    public long databaseSize() {
        return databaseFile.length();
    }

    public Map<String, String> readPropertiesTable() throws SQLException {
        var result = new HashMap<String, String>();
        try (var stmt = connection.createStatement();
             var rs = stmt.executeQuery("SELECT key, value FROM properties")) {
            while (rs.next()) {
                result.put(rs.getString(1), rs.getString(2));
            }
        }
        return result;
    }

    public Statement createStatement() throws SQLException {
        return connection.createStatement();
    }
}
