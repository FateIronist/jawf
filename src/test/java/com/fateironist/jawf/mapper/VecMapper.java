package com.fateironist.jawf.mapper;

import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * Mapper for sqlite-vec vector operations.
 * Uses raw SQL because vec0 virtual tables require binary blob parameters.
 */
@Mapper
public interface VecMapper {

    @Update("DROP TABLE IF EXISTS ${tableName}")
    void dropTable(@Param("tableName") String tableName);

    @Update("CREATE VIRTUAL TABLE ${tableName} USING vec0(id INTEGER PRIMARY KEY, embedding FLOAT[${dimension}])")
    void createVecTable(@Param("tableName") String tableName, @Param("dimension") int dimension);

    @Insert("INSERT INTO ${tableName} (id, embedding) VALUES (#{id}, #{embedding})")
    void insertVector(@Param("tableName") String tableName, @Param("id") long id, @Param("embedding") byte[] embedding);

    @Select("SELECT id, distance FROM ${tableName} WHERE embedding MATCH #{query} ORDER BY distance LIMIT #{k}")
    List<VectorResult> knnSearch(@Param("tableName") String tableName,
                                 @Param("query") byte[] query,
                                 @Param("k") int k);

    @Delete("DELETE FROM ${tableName} WHERE id = #{id}")
    int deleteVector(@Param("tableName") String tableName, @Param("id") long id);

    @Select("SELECT COUNT(*) FROM ${tableName}")
    int count(@Param("tableName") String tableName);

    /** KNN search result: row id + distance to query vector */
    record VectorResult(long id, double distance) {}
}
