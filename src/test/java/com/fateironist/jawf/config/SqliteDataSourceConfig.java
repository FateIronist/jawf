package com.fateironist.jawf.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.sqlite.SQLiteConfig;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Custom DataSource configuration for SQLite with vec0 extension support.
 * <p>
 * HikariCP does not pass JDBC URL query parameters to the driver,
 * so we configure SQLiteConfig explicitly to enable extension loading,
 * and eagerly load vec0 on the first connection.
 */
@Configuration
@Profile("test")
public class SqliteDataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    public DataSource dataSource(DataSourceProperties properties) throws Exception {
        SQLiteConfig sqliteConfig = new SQLiteConfig();
        sqliteConfig.enableLoadExtension(true);

        HikariDataSource ds = new HikariDataSource();
        ds.setDriverClassName(properties.getDriverClassName());
        ds.setJdbcUrl(properties.getUrl());
        ds.setUsername(properties.getUsername());
        ds.setPassword(properties.getPassword());
        ds.setDataSourceProperties(sqliteConfig.toProperties());

        // Eagerly load vec0 extension on a connection so it's available for all subsequent connections
        // (in-memory DB with cache=shared shares the schema across connections)
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("SELECT load_extension('vec0')");
        } catch (Exception e) {
            System.out.println("Note: vec0 extension could not be loaded: " + e.getMessage());
        }

        return ds;
    }
}
