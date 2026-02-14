package com.enterprise.testing.functional.db;

import com.enterprise.testing.shared.config.FrameworkConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

/**
 * Database assertion helper for verifying data persistence after test actions.
 * 
 * USAGE IN STEP DEFINITIONS:
 *   Map<String, Object> row = dbHelper.queryRow(
 *       "SELECT * FROM orders WHERE id = ?", orderId
 *   );
 *   assertThat(row.get("status")).isEqualTo("CONFIRMED");
 * 
 * Uses HikariCP connection pooling. Testcontainers provides the
 * JDBC URL when running in CI.
 */
public class DatabaseHelper implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DatabaseHelper.class);
    private final HikariDataSource dataSource;

    public DatabaseHelper(String jdbcUrl, String username, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(5);
        config.setConnectionTimeout(5000);

        this.dataSource = new HikariDataSource(config);
        log.info("Database connection pool initialized: {}", jdbcUrl);
    }

    public DatabaseHelper() {
        this(
                FrameworkConfig.getInstance().getDbUrl(),
                FrameworkConfig.getInstance().getDbUsername(),
                FrameworkConfig.getInstance().getDbPassword()
        );
    }

    /**
     * Query a single row and return as a Map.
     */
    public Map<String, Object> queryRow(String sql, Object... params) {
        List<Map<String, Object>> results = queryList(sql, params);
        if (results.isEmpty()) {
            return null;
        }
        if (results.size() > 1) {
            log.warn("Expected single row but got {}. Returning first.", results.size());
        }
        return results.get(0);
    }

    /**
     * Query multiple rows.
     */
    public List<Map<String, Object>> queryList(String sql, Object... params) {
        List<Map<String, Object>> results = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            setParameters(stmt, params);

            try (ResultSet rs = stmt.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();

                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= colCount; i++) {
                        row.put(meta.getColumnLabel(i).toLowerCase(), rs.getObject(i));
                    }
                    results.add(row);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database query failed: " + sql, e);
        }

        log.debug("Query returned {} rows: {}", results.size(), sql);
        return results;
    }

    /**
     * Execute a count query.
     */
    public long count(String table, String whereClause, Object... params) {
        String sql = "SELECT COUNT(*) FROM " + table +
                (whereClause != null ? " WHERE " + whereClause : "");

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            setParameters(stmt, params);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Count query failed: " + sql, e);
        }
    }

    /**
     * Execute an INSERT/UPDATE/DELETE.
     */
    public int execute(String sql, Object... params) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            setParameters(stmt, params);
            int affected = stmt.executeUpdate();
            log.debug("Executed SQL, {} rows affected: {}", affected, sql);
            return affected;
        } catch (SQLException e) {
            throw new RuntimeException("SQL execution failed: " + sql, e);
        }
    }

    /**
     * Execute a SQL script (for test setup/teardown).
     */
    public void executeScript(String script) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            for (String sql : script.split(";")) {
                String trimmed = sql.trim();
                if (!trimmed.isEmpty()) {
                    stmt.execute(trimmed);
                }
            }
            log.info("Executed SQL script ({} statements)", script.split(";").length);
        } catch (SQLException e) {
            throw new RuntimeException("Script execution failed", e);
        }
    }

    private void setParameters(PreparedStatement stmt, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            stmt.setObject(i + 1, params[i]);
        }
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            log.info("Database connection pool closed");
        }
    }
}
