package io.quarkus.narayana.jta;

import java.util.function.Function;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.NotSupportedException;
import jakarta.transaction.Status;
import jakarta.transaction.SystemException;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.transaction.UserTransaction;

import org.jboss.logging.Logger;

import io.quarkus.narayana.jta.runtime.ReadOnlyTransactionSynchronization;
import io.quarkus.narayana.jta.runtime.TransactionManagerConfiguration;

/**
 * A request scoped representation of a transaction.
 * <p>
 * If the transaction is not committed it will be automatically rolled back when the request scope is destroyed.
 */
@RequestScoped
class RequestScopedTransaction {

    private static final Logger log = Logger.getLogger(RequestScopedTransaction.class);
    public static final Function<Throwable, RunOptions.ExceptionResult> DEFAULT_HANDLER = (
            e) -> RunOptions.ExceptionResult.ROLLBACK;

    private final UserTransaction userTransaction;
    private final TransactionManager transactionManager;
    private final TransactionManagerConfiguration transactionManagerConfiguration;
    private final TransactionSynchronizationRegistry transactionSynchronizationRegistry;
    private Transaction createdTransaction;
    boolean autoCommit;

    @Inject
    public RequestScopedTransaction(UserTransaction userTransaction,
            TransactionManager transactionManager, TransactionManagerConfiguration transactionManagerConfiguration,
            TransactionSynchronizationRegistry transactionSynchronizationRegistry) {
        this.userTransaction = userTransaction;
        this.transactionManager = transactionManager;
        this.transactionManagerConfiguration = transactionManagerConfiguration;
        this.transactionSynchronizationRegistry = transactionSynchronizationRegistry;
    }

    public RequestScopedTransaction() {
        //for proxiability
        this.userTransaction = null;
        this.transactionManagerConfiguration = null;
        this.transactionManager = null;
        this.transactionSynchronizationRegistry = null;
    }

    void begin(BeginOptions options) {
        int timeout = options != null ? options.timeout : 0;
        boolean commitOnRequestScopeEnd = options != null && options.commitOnRequestScopeEnd;
        boolean readOnly = options != null && options.readOnly;
        if (commitOnRequestScopeEnd && readOnly) {
            throw new QuarkusTransactionException(
                    "Cannot use commitOnRequestScopeEnd() with readOnly(): read-only transactions are always rolled back");
        }
        try {
            if (userTransaction.getStatus() != Status.STATUS_NO_TRANSACTION) {
                throw new QuarkusTransactionException("Transaction already active");

            }
            this.autoCommit = commitOnRequestScopeEnd;
            if (timeout > 0) {
                userTransaction.setTransactionTimeout(timeout);
            }
            userTransaction.begin();
            createdTransaction = transactionManager.getTransaction();
        } catch (NotSupportedException | SystemException e) {
            throw new QuarkusTransactionException(e);
        } finally {
            if (timeout > 0) {
                try {
                    userTransaction.setTransactionTimeout(
                            (int) transactionManagerConfiguration.defaultTransactionTimeout().toSeconds());
                } catch (SystemException e) {
                    throw new QuarkusTransactionException(e);
                }
            }
        }
        if (readOnly) {
            ReadOnlyTransactionSynchronization.markReadOnly(transactionSynchronizationRegistry);
        }
    }

    @PreDestroy
    void destroy() {
        try {
            if (transactionManager.getTransaction() == createdTransaction) {
                if (autoCommit) {
                    QuarkusTransaction.commit();
                } else {
                    log.warn("Rolling back transaction that was not committed or explicitly rolled back.");
                    try {
                        userTransaction.rollback();
                    } catch (SystemException e) {
                        throw new QuarkusTransactionException(e);
                    }
                }
            }
        } catch (SystemException e) {
            log.warn("Failed to destroy request scoped transaction", e);
        }
    }

}
