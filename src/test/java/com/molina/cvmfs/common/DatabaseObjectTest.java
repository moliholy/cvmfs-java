package com.molina.cvmfs.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseObjectTest {

    @TempDir
    Path tempDir;

    private File createDb() throws Exception {
        var dbFile = tempDir.resolve("test.db").toFile();
        var conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        conn.setAutoCommit(true);
        conn.createStatement().executeUpdate(
                "CREATE TABLE properties (key TEXT, value TEXT)");
        conn.createStatement().executeUpdate(
                "INSERT INTO properties VALUES ('schema', '2.5')");
        conn.createStatement().executeUpdate(
                "INSERT INTO properties VALUES ('name', 'test-repo')");
        conn.close();
        return dbFile;
    }

    @Test
    void opensDatabase() throws Exception {
        var dbFile = createDb();
        var obj = new DatabaseObject(dbFile);
        assertTrue(obj.isOpen());
        obj.close();
    }

    @Test
    void closeSetsClosed() throws Exception {
        var dbFile = createDb();
        var obj = new DatabaseObject(dbFile);
        obj.close();
        assertFalse(obj.isOpen());
    }

    @Test
    void closeIsIdempotent() throws Exception {
        var dbFile = createDb();
        var obj = new DatabaseObject(dbFile);
        obj.close();
        obj.close();
        assertFalse(obj.isOpen());
    }

    @Test
    void readPropertiesTable() throws Exception {
        var dbFile = createDb();
        var obj = new DatabaseObject(dbFile);
        var props = obj.readPropertiesTable();
        assertEquals("2.5", props.get("schema"));
        assertEquals("test-repo", props.get("name"));
        assertEquals(2, props.size());
        obj.close();
    }

    @Test
    void databaseSize() throws Exception {
        var dbFile = createDb();
        var obj = new DatabaseObject(dbFile);
        assertTrue(obj.databaseSize() > 0);
        obj.close();
    }

    @Test
    void nullFileThrows() {
        assertThrows(IllegalStateException.class, () -> new DatabaseObject(null));
    }

    @Test
    void nonExistentFileThrows() {
        var f = new File("/nonexistent/path/db.sqlite");
        assertThrows(IllegalStateException.class, () -> new DatabaseObject(f));
    }

    @Test
    void createStatement() throws Exception {
        var dbFile = createDb();
        var obj = new DatabaseObject(dbFile);
        var stmt = obj.createStatement();
        assertNotNull(stmt);
        var rs = stmt.executeQuery("SELECT count(*) FROM properties");
        assertTrue(rs.next());
        assertEquals(2, rs.getInt(1));
        stmt.close();
        obj.close();
    }

    @Test
    void createPreparedStatement() throws Exception {
        var dbFile = createDb();
        var obj = new DatabaseObject(dbFile);
        var ps = obj.createPreparedStatement("SELECT value FROM properties WHERE key = ?");
        ps.setString(1, "schema");
        var rs = ps.executeQuery();
        assertTrue(rs.next());
        assertEquals("2.5", rs.getString(1));
        ps.close();
        obj.close();
    }
}
