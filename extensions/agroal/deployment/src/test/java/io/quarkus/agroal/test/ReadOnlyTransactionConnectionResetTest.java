package io.quarkus.agroal.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.runtime.ReadOnlyTransactionConnectionInterceptor;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.QuarkusExtensionTest;

public class ReadOnlyTransactionConnectionResetTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = new QuarkusExtensionTest()
            .withApplicationRoot((jar) -> jar
                    .addClasses(ReadOnlyTransactionConnectionResetTest.class,
                            ReadOnlyTransactionConnectionInterceptor.class))
            .withConfigurationResource("base.properties")
            .overrideConfigKey("quarkus.datasource.jdbc.max-size", "1");

    @Inject
    AgroalDataSource dataSource;

    @Test
    void connectionReadOnlyIsResetAfterReadOnlyTransaction() throws SQLException {
        // Create a table for the test
        QuarkusTransaction.requiringNew().run(() -> {
            try (Connection conn = dataSource.getConnection();
                    Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS read_only_test (id INT PRIMARY KEY, name VARCHAR(100))");
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });

        // Run a read-only transaction that acquires the (sole) connection
        QuarkusTransaction.requiringNew().readOnly().run(() -> {
            try (Connection conn = dataSource.getConnection();
                    Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM read_only_test")) {
                rs.next();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });

        // With max-size=1, this MUST reuse the same connection.
        // If setReadOnly(false) was not called on return, this INSERT will fail.
        QuarkusTransaction.requiringNew().run(() -> {
            try (Connection conn = dataSource.getConnection();
                    Statement stmt = conn.createStatement()) {
                assertThat(conn.isReadOnly()).as("Connection should not be read-only after reset").isFalse();
                stmt.execute("INSERT INTO read_only_test (id, name) VALUES (1, 'test')");
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });

        // Verify the insert succeeded
        QuarkusTransaction.requiringNew().run(() -> {
            try (Connection conn = dataSource.getConnection();
                    Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT name FROM read_only_test WHERE id = 1")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("name")).isEqualTo("test");
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
