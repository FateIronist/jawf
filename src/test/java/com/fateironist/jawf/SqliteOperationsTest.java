package com.fateironist.jawf;

import org.junit.jupiter.api.*;
import org.sqlite.SQLiteConfig;

import java.nio.file.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

/**
 * SQLite CRUD + Vector (vec0) operations test.
 * <p>
 * Uses a temporary file-based database (matches application.yaml's vec0 config).
 * Each CRUD test recreates the schema for isolation.
 * <p>
 * Vector tests require the sqlite-vec (vec0) extension to be installed.
 * If not available, vector tests are skipped (not failed).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SqliteOperationsTest {

    private static Connection conn;
    private static Path dbFile;
    private static boolean vecAvailable;

    @BeforeAll
    static void setUp() throws Exception {
        Class.forName("org.sqlite.JDBC");

        // Connect with extension loading enabled + vec_auto_download (same as application.yaml)
        // Use file: URI to avoid Windows path parsing issues with '?'
        dbFile = Files.createTempFile("jawf-test-", ".db");
        String url = "jdbc:sqlite:file:" + dbFile.toUri().getPath()
                + "?load_extension=vec0&vec_auto_download=true";
        SQLiteConfig config = new SQLiteConfig();
        config.enableLoadExtension(true);
        conn = DriverManager.getConnection(url, config.toProperties());
        conn.setAutoCommit(false);

        // Try multiple ways to load vec0
        vecAvailable = isVec0Usable();
        if (!vecAvailable) {
            // 1) Try loading by name (rely on PATH / java.library.path)
            vecAvailable = tryLoadExtension("vec0");
        }
        if (!vecAvailable) {
            // 2) Try loading from project root (vec0.dll next to pom.xml)
            Path projectRoot = Path.of(System.getProperty("user.dir"));
            Path vecDll = projectRoot.resolve("vec0.dll");
            if (Files.exists(vecDll)) {
                vecAvailable = tryLoadExtension(vecDll.toAbsolutePath().toString());
            }
        }
        System.out.println(vecAvailable
                ? "✓ vec0 extension loaded — vector tests will run"
                : "✗ vec0 not available — vector tests will be skipped");
    }

    private static boolean tryLoadExtension(String path) {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("SELECT load_extension('" + path.replace("'", "''") + "')");
            return isVec0Usable();
        } catch (SQLException e) {
            System.out.println("  load_extension('" + path + "') failed: " + e.getMessage());
            return false;
        }
    }

    /** Quick check: can we actually use vec0 virtual tables? */
    private static boolean isVec0Usable() {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS _vec_probe");
            stmt.execute("CREATE VIRTUAL TABLE _vec_probe USING vec0(id INTEGER PRIMARY KEY, v FLOAT[2])");
            stmt.execute("DROP TABLE _vec_probe");
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (conn != null && !conn.isClosed()) {
            conn.close();
        }
        if (dbFile != null) {
            Files.deleteIfExists(dbFile);
        }
    }

    // ==================== Helper ====================

    private void createUsersTable() throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS users");
            stmt.execute("""
                    CREATE TABLE users (
                        id    INTEGER PRIMARY KEY AUTOINCREMENT,
                        name  TEXT    NOT NULL,
                        email TEXT    NOT NULL UNIQUE,
                        age   INTEGER
                    )
                    """);
        }
        conn.commit();
    }

    // ==================== CRUD Tests ====================

    @Test
    @Order(1)
    @DisplayName("INSERT - 插入单条记录")
    void testInsert() throws SQLException {
        createUsersTable();

        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO users (name, email, age) VALUES (?, ?, ?)")) {
            ps.setString(1, "Alice");
            ps.setString(2, "alice@example.com");
            ps.setInt(3, 30);
            assertEquals(1, ps.executeUpdate());
        }
        conn.commit();

        // Verify
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE email = ?")) {
            ps.setString(1, "alice@example.com");
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next());
            assertEquals("Alice", rs.getString("name"));
            assertEquals(30, rs.getInt("age"));
        }
    }

    @Test
    @Order(2)
    @DisplayName("INSERT - 批量插入多条记录")
    void testBatchInsert() throws SQLException {
        createUsersTable();

        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO users (name, email, age) VALUES (?, ?, ?)")) {
            String[][] rows = {
                    {"Bob", "bob@example.com", "25"},
                    {"Charlie", "charlie@example.com", "35"},
                    {"Diana", "diana@example.com", "28"},
            };
            for (String[] row : rows) {
                ps.setString(1, row[0]);
                ps.setString(2, row[1]);
                ps.setInt(3, Integer.parseInt(row[2]));
                ps.addBatch();
            }
            int[] results = ps.executeBatch();
            assertEquals(3, results.length);
        }
        conn.commit();

        // Verify count
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS cnt FROM users")) {
            assertTrue(rs.next());
            assertEquals(3, rs.getInt("cnt"));
        }
    }

    @Test
    @Order(3)
    @DisplayName("SELECT - 查询单条与多条记录")
    void testSelect() throws SQLException {
        createUsersTable();

        // Seed data
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO users (name, email, age) VALUES (?, ?, ?)")) {
            for (String[] row : new String[][]{
                    {"Eve", "eve@example.com", "22"},
                    {"Frank", "frank@example.com", "40"},
                    {"Grace", "grace@example.com", "33"},
            }) {
                ps.setString(1, row[0]);
                ps.setString(2, row[1]);
                ps.setInt(3, Integer.parseInt(row[2]));
                ps.addBatch();
            }
            ps.executeBatch();
        }
        conn.commit();

        // Select single
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE name = ?")) {
            ps.setString(1, "Eve");
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next());
            assertEquals("eve@example.com", rs.getString("email"));
            assertFalse(rs.next());
        }

        // Select multiple with condition
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE age > ? ORDER BY age")) {
            ps.setInt(1, 25);
            ResultSet rs = ps.executeQuery();
            List<String> names = new ArrayList<>();
            while (rs.next()) {
                names.add(rs.getString("name"));
            }
            assertEquals(List.of("Grace", "Frank"), names);
        }
    }

    @Test
    @Order(4)
    @DisplayName("UPDATE - 更新记录")
    void testUpdate() throws SQLException {
        createUsersTable();

        // Insert
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO users (name, email, age) VALUES (?, ?, ?)")) {
            ps.setString(1, "Hank");
            ps.setString(2, "hank@example.com");
            ps.setInt(3, 29);
            ps.executeUpdate();
        }
        conn.commit();

        // Update
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE users SET age = ?, name = ? WHERE email = ?")) {
            ps.setInt(1, 30);
            ps.setString(2, "Hank Jr.");
            ps.setString(3, "hank@example.com");
            assertEquals(1, ps.executeUpdate());
        }
        conn.commit();

        // Verify
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE email = ?")) {
            ps.setString(1, "hank@example.com");
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next());
            assertEquals("Hank Jr.", rs.getString("name"));
            assertEquals(30, rs.getInt("age"));
        }
    }

    @Test
    @Order(5)
    @DisplayName("DELETE - 删除记录")
    void testDelete() throws SQLException {
        createUsersTable();

        // Insert
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO users (name, email, age) VALUES (?, ?, ?)")) {
            for (String[] row : new String[][]{
                    {"Ivan", "ivan@example.com", "31"},
                    {"Jack", "jack@example.com", "27"},
            }) {
                ps.setString(1, row[0]);
                ps.setString(2, row[1]);
                ps.setInt(3, Integer.parseInt(row[2]));
                ps.addBatch();
            }
            ps.executeBatch();
        }
        conn.commit();

        // Delete one
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM users WHERE email = ?")) {
            ps.setString(1, "ivan@example.com");
            assertEquals(1, ps.executeUpdate());
        }
        conn.commit();

        // Verify only Jack remains
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM users")) {
            assertTrue(rs.next());
            assertEquals("Jack", rs.getString("name"));
            assertFalse(rs.next());
        }
    }

    @Test
    @Order(6)
    @DisplayName("TRANSACTION - 事务回滚")
    void testTransactionRollback() throws SQLException {
        createUsersTable();

        // Insert initial record
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO users (name, email, age) VALUES (?, ?, ?)")) {
            ps.setString(1, "Kate");
            ps.setString(2, "kate@example.com");
            ps.setInt(3, 26);
            ps.executeUpdate();
        }
        conn.commit();

        // Attempt update then rollback
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE users SET age = 99 WHERE email = ?")) {
            ps.setString(1, "kate@example.com");
            ps.executeUpdate();
        }
        conn.rollback(); // Rollback the update

        // Verify original value is intact
        try (PreparedStatement ps = conn.prepareStatement("SELECT age FROM users WHERE email = ?")) {
            ps.setString(1, "kate@example.com");
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next());
            assertEquals(26, rs.getInt("age"));
        }
    }

    // ==================== Vector Operations Tests ====================
    // These tests require the sqlite-vec (vec0) extension.
    // Install: https://alexgarcia.xyz/sqlite-vec/

    @Test
    @Order(10)
    @DisplayName("VECTOR - 创建 vec0 虚拟表")
    void testCreateVecTable() throws SQLException {
        assumeTrue(vecAvailable, "vec0 extension not available");

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS vec_items");
            stmt.execute("""
                    CREATE VIRTUAL TABLE vec_items USING vec0(
                        id    INTEGER PRIMARY KEY,
                        embedding FLOAT[4]
                    )
                    """);
        }
        conn.commit();

        // Verify table exists (vec0 tables show as 'table' type)
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT name FROM sqlite_master WHERE type='table' AND name=?")) {
            ps.setString(1, "vec_items");
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next());
        }
    }

    @Test
    @Order(11)
    @DisplayName("VECTOR - 插入向量数据")
    void testInsertVectors() throws SQLException {
        assumeTrue(vecAvailable, "vec0 extension not available");

        // Ensure table exists
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS vec_items");
            stmt.execute("""
                    CREATE VIRTUAL TABLE vec_items USING vec0(
                        id INTEGER PRIMARY KEY,
                        embedding FLOAT[4]
                    )
                    """);
        }

        // Insert vectors as little-endian float bytes
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO vec_items (id, embedding) VALUES (?, ?)")) {

            // Item 1: [1.0, 0.0, 0.0, 0.0]
            ps.setInt(1, 1);
            ps.setBytes(2, floatArrayToBytes(new float[]{1.0f, 0.0f, 0.0f, 0.0f}));
            ps.addBatch();

            // Item 2: [0.0, 1.0, 0.0, 0.0]
            ps.setInt(1, 2);
            ps.setBytes(2, floatArrayToBytes(new float[]{0.0f, 1.0f, 0.0f, 0.0f}));
            ps.addBatch();

            // Item 3: [0.9, 0.1, 0.0, 0.0] - close to item 1
            ps.setInt(1, 3);
            ps.setBytes(2, floatArrayToBytes(new float[]{0.9f, 0.1f, 0.0f, 0.0f}));
            ps.addBatch();

            // Item 4: [0.0, 0.0, 1.0, 0.0]
            ps.setInt(1, 4);
            ps.setBytes(2, floatArrayToBytes(new float[]{0.0f, 0.0f, 1.0f, 0.0f}));
            ps.addBatch();

            int[] results = ps.executeBatch();
            assertEquals(4, results.length);
        }
        conn.commit();

        // Verify count
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS cnt FROM vec_items")) {
            assertTrue(rs.next());
            assertEquals(4, rs.getInt("cnt"));
        }
    }

    @Test
    @Order(12)
    @DisplayName("VECTOR - KNN 近邻搜索")
    void testVectorKnnSearch() throws SQLException {
        assumeTrue(vecAvailable, "vec0 extension not available");

        // Ensure table and data
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS vec_items");
            stmt.execute("""
                    CREATE VIRTUAL TABLE vec_items USING vec0(
                        id INTEGER PRIMARY KEY,
                        embedding FLOAT[4]
                    )
                    """);

            // Seed vectors
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO vec_items (id, embedding) VALUES (?, ?)")) {
                ps.setInt(1, 1);
                ps.setBytes(2, floatArrayToBytes(new float[]{1.0f, 0.0f, 0.0f, 0.0f}));
                ps.executeUpdate();

                ps.setInt(1, 2);
                ps.setBytes(2, floatArrayToBytes(new float[]{0.0f, 1.0f, 0.0f, 0.0f}));
                ps.executeUpdate();

                ps.setInt(1, 3);
                ps.setBytes(2, floatArrayToBytes(new float[]{0.9f, 0.1f, 0.0f, 0.0f}));
                ps.executeUpdate();

                ps.setInt(1, 4);
                ps.setBytes(2, floatArrayToBytes(new float[]{0.0f, 0.0f, 1.0f, 0.0f}));
                ps.executeUpdate();
            }
        }
        conn.commit();

        // KNN search: find 2 nearest neighbors to [1.0, 0.0, 0.0, 0.0]
        // Expected: id=1 (distance=0) and id=3 (distance ~0.14)
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, distance FROM vec_items WHERE embedding MATCH ? ORDER BY distance LIMIT ?")) {
            ps.setBytes(1, floatArrayToBytes(new float[]{1.0f, 0.0f, 0.0f, 0.0f}));
            ps.setInt(2, 2);
            ResultSet rs = ps.executeQuery();

            assertTrue(rs.next(), "Should have at least one result");
            assertEquals(1, rs.getInt("id"), "Nearest should be id=1");

            assertTrue(rs.next(), "Should have second result");
            assertEquals(3, rs.getInt("id"), "Second nearest should be id=3");
        }
    }

    @Test
    @Order(13)
    @DisplayName("VECTOR - 删除向量数据")
    void testDeleteVector() throws SQLException {
        assumeTrue(vecAvailable, "vec0 extension not available");

        // Ensure table and data
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS vec_items");
            stmt.execute("""
                    CREATE VIRTUAL TABLE vec_items USING vec0(
                        id INTEGER PRIMARY KEY,
                        embedding FLOAT[4]
                    )
                    """);

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO vec_items (id, embedding) VALUES (?, ?)")) {
                ps.setInt(1, 1);
                ps.setBytes(2, floatArrayToBytes(new float[]{1.0f, 0.0f, 0.0f, 0.0f}));
                ps.executeUpdate();

                ps.setInt(1, 2);
                ps.setBytes(2, floatArrayToBytes(new float[]{0.0f, 1.0f, 0.0f, 0.0f}));
                ps.executeUpdate();
            }
        }
        conn.commit();

        // Delete one vector
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM vec_items WHERE id = ?")) {
            ps.setInt(1, 1);
            assertEquals(1, ps.executeUpdate());
        }
        conn.commit();

        // Verify only id=2 remains
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS cnt FROM vec_items")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt("cnt"));
        }

        // KNN search now should only find id=2
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM vec_items WHERE embedding MATCH ? ORDER BY distance LIMIT ?")) {
            ps.setBytes(1, floatArrayToBytes(new float[]{1.0f, 0.0f, 0.0f, 0.0f}));
            ps.setInt(2, 5);
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next());
            assertEquals(2, rs.getInt("id"));
            assertFalse(rs.next());
        }
    }

    @Test
    @Order(14)
    @DisplayName("VECTOR - 高维向量 (128维)")
    void testHighDimensionalVector() throws SQLException {
        assumeTrue(vecAvailable, "vec0 extension not available");

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS vec_high_dim");
            stmt.execute("""
                    CREATE VIRTUAL TABLE vec_high_dim USING vec0(
                        id INTEGER PRIMARY KEY,
                        embedding FLOAT[128]
                    )
                    """);
        }

        // Create two 128-dim vectors
        float[] v1 = new float[128];
        float[] v2 = new float[128];
        for (int i = 0; i < 128; i++) {
            v1[i] = (i % 2 == 0) ? 1.0f : 0.0f;
            v2[i] = (i % 2 == 0) ? 0.0f : 1.0f;
        }

        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO vec_high_dim (id, embedding) VALUES (?, ?)")) {
            ps.setInt(1, 1);
            ps.setBytes(2, floatArrayToBytes(v1));
            ps.executeUpdate();

            ps.setInt(1, 2);
            ps.setBytes(2, floatArrayToBytes(v2));
            ps.executeUpdate();
        }
        conn.commit();

        // Search nearest to v1 - should return id=1
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, distance FROM vec_high_dim WHERE embedding MATCH ? ORDER BY distance LIMIT 1")) {
            ps.setBytes(1, floatArrayToBytes(v1));
            ResultSet rs = ps.executeQuery();
            assertTrue(rs.next());
            assertEquals(1, rs.getInt("id"));
        }
    }

    // ==================== Utility ====================

    /**
     * Convert a float array to little-endian bytes for vec0 storage.
     */
    private static byte[] floatArrayToBytes(float[] floats) {
        byte[] bytes = new byte[floats.length * 4];
        for (int i = 0; i < floats.length; i++) {
            int bits = Float.floatToIntBits(floats[i]);
            bytes[i * 4]     = (byte) (bits & 0xFF);
            bytes[i * 4 + 1] = (byte) ((bits >> 8) & 0xFF);
            bytes[i * 4 + 2] = (byte) ((bits >> 16) & 0xFF);
            bytes[i * 4 + 3] = (byte) ((bits >> 24) & 0xFF);
        }
        return bytes;
    }
}
