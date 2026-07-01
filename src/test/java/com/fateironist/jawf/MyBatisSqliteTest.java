package com.fateironist.jawf;

import com.fateironist.jawf.mapper.UserMapper;
import com.fateironist.jawf.mapper.VecMapper;
import com.fateironist.jawf.mapper.VecMapper.VectorResult;
import com.fateironist.jawf.model.User;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

/**
 * MyBatis-based SQLite CRUD + Vector operations test.
 * <p>
 * Uses Spring Boot test context with in-memory SQLite database.
 * Vector tests require the vec0 extension (skipped if unavailable).
 */
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(Lifecycle.PER_CLASS)
class MyBatisSqliteTest {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private VecMapper vecMapper;

    private boolean vecAvailable;

    @BeforeAll
    void setUp() {
        // Create users table
        userMapper.dropTable();
        userMapper.createTable();

        // Check vec0 extension availability
        try {
            vecMapper.dropTable("_vec_probe");
            vecMapper.createVecTable("_vec_probe", 2);
            vecMapper.dropTable("_vec_probe");
            vecAvailable = true;
            System.out.println("✓ vec0 available — vector tests will run");
        } catch (Exception e) {
            System.out.println("✗ vec0 not available — vector tests skipped: " + e.getMessage());
        }
    }

    // ==================== CRUD Tests ====================

    @Test
    @Order(1)
    @DisplayName("INSERT - 插入单条记录")
    void testInsert() {
        User user = new User();
        user.setName("Alice");
        user.setEmail("alice@example.com");
        user.setAge(30);
        int rows = userMapper.insert(user);

        assertEquals(1, rows);
        assertNotNull(user.getId(), "Generated key should be set");

        User found = userMapper.selectById(user.getId());
        assertEquals("Alice", found.getName());
        assertEquals("alice@example.com", found.getEmail());
        assertEquals(30, found.getAge());
    }

    @Test
    @Order(2)
    @DisplayName("INSERT - 批量插入多条记录")
    void testBatchInsert() {
        userMapper.deleteAll();

        for (String[] data : new String[][]{
                {"Bob", "bob@example.com", "25"},
                {"Charlie", "charlie@example.com", "35"},
                {"Diana", "diana@example.com", "28"},
        }) {
            User u = new User();
            u.setName(data[0]);
            u.setEmail(data[1]);
            u.setAge(Integer.parseInt(data[2]));
            userMapper.insert(u);
        }

        List<User> all = userMapper.selectAll();
        assertEquals(3, all.size());
    }

    @Test
    @Order(3)
    @DisplayName("SELECT - 按条件查询")
    void testSelect() {
        userMapper.deleteAll();

        User eve = new User();
        eve.setName("Eve");
        eve.setEmail("eve@example.com");
        eve.setAge(22);
        userMapper.insert(eve);

        User frank = new User();
        frank.setName("Frank");
        frank.setEmail("frank@example.com");
        frank.setAge(40);
        userMapper.insert(frank);

        User grace = new User();
        grace.setName("Grace");
        grace.setEmail("grace@example.com");
        grace.setAge(33);
        userMapper.insert(grace);

        // By email
        User found = userMapper.selectByEmail("eve@example.com");
        assertNotNull(found);
        assertEquals("Eve", found.getName());

        // By age condition
        List<User> older = userMapper.selectByAgeGreaterThan(25);
        assertEquals(2, older.size());
        assertEquals("Grace", older.get(0).getName());  // age 33
        assertEquals("Frank", older.get(1).getName());  // age 40
    }

    @Test
    @Order(4)
    @DisplayName("UPDATE - 更新记录")
    void testUpdate() {
        userMapper.deleteAll();

        User user = new User();
        user.setName("Hank");
        user.setEmail("hank@example.com");
        user.setAge(29);
        userMapper.insert(user);

        user.setName("Hank Jr.");
        user.setAge(30);
        int rows = userMapper.update(user);
        assertEquals(1, rows);

        User found = userMapper.selectByEmail("hank@example.com");
        assertEquals("Hank Jr.", found.getName());
        assertEquals(30, found.getAge());
    }

    @Test
    @Order(5)
    @DisplayName("DELETE - 删除记录")
    void testDelete() {
        userMapper.deleteAll();

        User u1 = new User();
        u1.setName("Ivan");
        u1.setEmail("ivan@example.com");
        u1.setAge(31);
        userMapper.insert(u1);

        User u2 = new User();
        u2.setName("Jack");
        u2.setEmail("jack@example.com");
        u2.setAge(27);
        userMapper.insert(u2);

        assertEquals(1, userMapper.deleteById(u1.getId()));

        List<User> remaining = userMapper.selectAll();
        assertEquals(1, remaining.size());
        assertEquals("Jack", remaining.get(0).getName());
    }

    // ==================== Vector Tests ====================

    private static final String VEC_TABLE = "vec_test_items";

    @Test
    @Order(10)
    @DisplayName("VECTOR - 创建 vec0 虚拟表")
    void testCreateVecTable() {
        assumeTrue(vecAvailable, "vec0 not available");

        vecMapper.dropTable(VEC_TABLE);
        assertDoesNotThrow(() -> vecMapper.createVecTable(VEC_TABLE, 4));
    }

    @Test
    @Order(11)
    @DisplayName("VECTOR - 插入向量数据")
    void testInsertVectors() {
        assumeTrue(vecAvailable, "vec0 not available");

        vecMapper.dropTable(VEC_TABLE);
        vecMapper.createVecTable(VEC_TABLE, 4);

        vecMapper.insertVector(VEC_TABLE, 1, toBytes(1.0f, 0.0f, 0.0f, 0.0f));
        vecMapper.insertVector(VEC_TABLE, 2, toBytes(0.0f, 1.0f, 0.0f, 0.0f));
        vecMapper.insertVector(VEC_TABLE, 3, toBytes(0.9f, 0.1f, 0.0f, 0.0f));
        vecMapper.insertVector(VEC_TABLE, 4, toBytes(0.0f, 0.0f, 1.0f, 0.0f));

        assertEquals(4, vecMapper.count(VEC_TABLE));
    }

    @Test
    @Order(12)
    @DisplayName("VECTOR - KNN 近邻搜索")
    void testKnnSearch() {
        assumeTrue(vecAvailable, "vec0 not available");

        vecMapper.dropTable(VEC_TABLE);
        vecMapper.createVecTable(VEC_TABLE, 4);

        vecMapper.insertVector(VEC_TABLE, 1, toBytes(1.0f, 0.0f, 0.0f, 0.0f));
        vecMapper.insertVector(VEC_TABLE, 2, toBytes(0.0f, 1.0f, 0.0f, 0.0f));
        vecMapper.insertVector(VEC_TABLE, 3, toBytes(0.9f, 0.1f, 0.0f, 0.0f));
        vecMapper.insertVector(VEC_TABLE, 4, toBytes(0.0f, 0.0f, 1.0f, 0.0f));

        // Search 2 nearest to [1.0, 0.0, 0.0, 0.0]
        List<VectorResult> results = vecMapper.knnSearch(
                VEC_TABLE, toBytes(1.0f, 0.0f, 0.0f, 0.0f), 2);

        assertEquals(2, results.size());
        assertEquals(1, results.get(0).id());   // exact match
        assertEquals(3, results.get(1).id());   // [0.9, 0.1, 0, 0] is closest
    }

    @Test
    @Order(13)
    @DisplayName("VECTOR - 删除向量后验证搜索")
    void testDeleteVector() {
        assumeTrue(vecAvailable, "vec0 not available");

        vecMapper.dropTable(VEC_TABLE);
        vecMapper.createVecTable(VEC_TABLE, 4);

        vecMapper.insertVector(VEC_TABLE, 1, toBytes(1.0f, 0.0f, 0.0f, 0.0f));
        vecMapper.insertVector(VEC_TABLE, 2, toBytes(0.0f, 1.0f, 0.0f, 0.0f));

        assertEquals(1, vecMapper.deleteVector(VEC_TABLE, 1));
        assertEquals(1, vecMapper.count(VEC_TABLE));

        // Only id=2 should be found
        List<VectorResult> results = vecMapper.knnSearch(
                VEC_TABLE, toBytes(1.0f, 0.0f, 0.0f, 0.0f), 5);
        assertEquals(1, results.size());
        assertEquals(2, results.get(0).id());
    }

    @Test
    @Order(14)
    @DisplayName("VECTOR - 高维向量 (128维)")
    void testHighDimensionalVector() {
        assumeTrue(vecAvailable, "vec0 not available");

        vecMapper.dropTable("vec_high_dim");
        vecMapper.createVecTable("vec_high_dim", 128);

        float[] v1 = new float[128];
        float[] v2 = new float[128];
        for (int i = 0; i < 128; i++) {
            v1[i] = (i % 2 == 0) ? 1.0f : 0.0f;
            v2[i] = (i % 2 == 0) ? 0.0f : 1.0f;
        }

        vecMapper.insertVector("vec_high_dim", 1, toBytes(v1));
        vecMapper.insertVector("vec_high_dim", 2, toBytes(v2));

        List<VectorResult> results = vecMapper.knnSearch("vec_high_dim", toBytes(v1), 1);
        assertEquals(1, results.size());
        assertEquals(1, results.get(0).id());
    }

    // ==================== Utility ====================

    /** Convert float array to little-endian byte array for vec0 storage. */
    private static byte[] toBytes(float... floats) {
        ByteBuffer buf = ByteBuffer.allocate(floats.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float f : floats) {
            buf.putFloat(f);
        }
        return buf.array();
    }
}
