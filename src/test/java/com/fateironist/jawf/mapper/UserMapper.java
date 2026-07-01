package com.fateironist.jawf.mapper;

import com.fateironist.jawf.model.User;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface UserMapper {

    @Update("DROP TABLE IF EXISTS users")
    void dropTable();

    @Update("""
            CREATE TABLE IF NOT EXISTS users (
                id    INTEGER PRIMARY KEY AUTOINCREMENT,
                name  TEXT    NOT NULL,
                email TEXT    NOT NULL UNIQUE,
                age   INTEGER
            )
            """)
    void createTable();

    @Insert("INSERT INTO users (name, email, age) VALUES (#{name}, #{email}, #{age})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    @Select("SELECT * FROM users WHERE id = #{id}")
    User selectById(Long id);

    @Select("SELECT * FROM users WHERE email = #{email}")
    User selectByEmail(String email);

    @Select("SELECT * FROM users WHERE age > #{minAge} ORDER BY age")
    List<User> selectByAgeGreaterThan(Integer minAge);

    @Select("SELECT * FROM users ORDER BY id")
    List<User> selectAll();

    @Update("UPDATE users SET name = #{name}, email = #{email}, age = #{age} WHERE id = #{id}")
    int update(User user);

    @Delete("DELETE FROM users WHERE id = #{id}")
    int deleteById(Long id);

    @Delete("DELETE FROM users")
    int deleteAll();
}
