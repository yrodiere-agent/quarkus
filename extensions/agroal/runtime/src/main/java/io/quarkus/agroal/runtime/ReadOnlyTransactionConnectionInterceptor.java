package io.quarkus.agroal.runtime;

import java.sql.Connection;
import java.sql.SQLException;

import jakarta.transaction.TransactionSynchronizationRegistry;

import org.jboss.logging.Logger;

import io.agroal.api.AgroalPoolInterceptor;
import io.quarkus.narayana.jta.runtime.ReadOnlyTransactionSynchronization;

/**
 * An {@link AgroalPoolInterceptor} that sets {@link Connection#setReadOnly(boolean) Connection.setReadOnly(true)}
 * on connections acquired within a read-only transaction, and resets it when the connection is returned to the pool.
 * <p>
 * When the datasource's JDBC driver enforces read-only mode, this interceptor also registers the datasource
 * as a read-only enforcing resource in the {@link TransactionSynchronizationRegistry}, enabling the transaction
 * manager to commit (rather than roll back) the read-only transaction.
 * <p>
 * One instance is created per datasource in {@link DataSources#createDataSource}.
 * <p>
 * TODO once Narayana implements Jakarta Transactions read-only, replace
 * {@link ReadOnlyTransactionSynchronization#isReadOnly(TransactionSynchronizationRegistry)}
 * with {@code TransactionSynchronizationRegistry.isReadOnly()}.
 * See https://github.com/jakartaee/transactions/pull/222
 */
public class ReadOnlyTransactionConnectionInterceptor implements AgroalPoolInterceptor {

    private static final Logger log = Logger.getLogger(ReadOnlyTransactionConnectionInterceptor.class);

    private final TransactionSynchronizationRegistry transactionSynchronizationRegistry;
    private final String dataSourceName;
    private final boolean readOnlyEnforced;

    public ReadOnlyTransactionConnectionInterceptor(TransactionSynchronizationRegistry transactionSynchronizationRegistry,
            String dataSourceName, boolean readOnlyEnforced) {
        this.transactionSynchronizationRegistry = transactionSynchronizationRegistry;
        this.dataSourceName = dataSourceName;
        this.readOnlyEnforced = readOnlyEnforced;
    }

    @Override
    public void onConnectionAcquire(Connection connection) {
        if (ReadOnlyTransactionSynchronization.isReadOnly(transactionSynchronizationRegistry)) {
            try {
                connection.setReadOnly(true);
            } catch (SQLException e) {
                log.warnf(e, "Failed to set connection to read-only mode");
            }
            if (readOnlyEnforced) {
                ReadOnlyTransactionSynchronization.addReadOnlyEnforcingResource(
                        transactionSynchronizationRegistry, dataSourceName);
            }
        }
    }

    @Override
    public void onConnectionReturn(Connection connection) {
        try {
            if (connection.isReadOnly()) {
                connection.setReadOnly(false);
            }
        } catch (SQLException e) {
            log.warnf(e, "Failed to reset connection read-only mode");
        }
    }
}
