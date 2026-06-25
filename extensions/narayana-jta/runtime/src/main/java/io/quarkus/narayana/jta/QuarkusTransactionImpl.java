package io.quarkus.narayana.jta;

import java.util.concurrent.Callable;

import jakarta.transaction.HeuristicMixedException;
import jakarta.transaction.HeuristicRollbackException;
import jakarta.transaction.NotSupportedException;
import jakarta.transaction.RollbackException;
import jakarta.transaction.Status;
import jakarta.transaction.SystemException;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.transaction.UserTransaction;

import org.jboss.logging.Logger;

import io.quarkus.arc.Arc;
import io.quarkus.narayana.jta.runtime.ReadOnlyTransactionSynchronization;
import io.quarkus.narayana.jta.runtime.TransactionManagerConfiguration;

class QuarkusTransactionImpl {

    private static final Logger log = Logger.getLogger(QuarkusTransactionImpl.class);
    private static TransactionManager cachedTransactionManager;
    private static UserTransaction cachedUserTransaction;

    public static <T> T call(RunOptionsBase options, Callable<T> task) {
        switch (options.semantics) {
            case REQUIRE_NEW:
                return callRequireNew(options, task);
            case DISALLOW_EXISTING:
                return callDisallowExisting(options, task);
            case JOIN_EXISTING:
                return callJoinExisting(options, task);
            case SUSPEND_EXISTING:
                return callSuspendExisting(options, task);
        }
        throw new IllegalArgumentException("Unknown semantics");
    }

    private static <T> T callSuspendExisting(RunOptionsBase options, Callable<T> task) {
        if (options.exceptionHandler != null) {
            throw new IllegalStateException("Cannot specify both an exception handler and SUSPEND_EXISTING");
        }
        TransactionManager transactionManager = getTransactionManager();
        Transaction transaction = null;
        try {
            if (isTransactionActive()) {
                transaction = transactionManager.suspend();
            }
            T result = task.call();
            if (transaction != null) {
                try {
                    transactionManager.resume(transaction);
                    transaction = null;
                } catch (Exception e) {
                    throw new QuarkusTransactionException(e);
                }
            }
            return result;
        } catch (Exception e) {
            if (transaction != null) {
                try {
                    transactionManager.resume(transaction);
                } catch (Exception ex) {
                    e.addSuppressed(ex);
                }
            }
            if (e instanceof QuarkusTransactionException) {
                throw (QuarkusTransactionException) e;
            }
            throw new QuarkusTransactionException(e);
        }
    }

    private static <T> T callJoinExisting(RunOptionsBase options, Callable<T> task) {
        if (isTransactionActive()) {
            return callInTheirTx(options, task);
        } else {
            return callInOurTx(options, task);
        }
    }

    private static boolean isTransactionActive() {
        try {
            int status = getUserTransaction().getStatus();
            return status != Status.STATUS_NO_TRANSACTION;
        } catch (SystemException e) {
            throw new QuarkusTransactionException(e);
        }
    }

    private static <T> T callDisallowExisting(RunOptionsBase options, Callable<T> task) {
        if (isTransactionActive()) {
            throw new QuarkusTransactionException(new IllegalStateException("Transaction already active"));
        }
        return callInOurTx(options, task);
    }

    private static <T> T callRequireNew(RunOptionsBase options, Callable<T> task) {
        TransactionManager transactionManager = getTransactionManager();
        Transaction transaction = null;
        try {
            if (isTransactionActive()) {
                transaction = transactionManager.suspend();
            }
            T result = callInOurTx(options, task);
            if (transaction != null) {
                try {
                    transactionManager.resume(transaction);
                    transaction = null;
                } catch (Exception e) {
                    throw new QuarkusTransactionException(e);
                }
            }
            return result;
        } catch (Exception e) {
            if (transaction != null) {
                try {
                    transactionManager.resume(transaction);
                } catch (Exception ex) {
                    e.addSuppressed(ex);
                }
            }
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new QuarkusTransactionException(e);
        }
    }

    // TODO once Narayana implements Jakarta Transactions read-only, the readOnly field could be
    //  replaced by transaction.isReadOnly() to decide whether to rollback instead of commit.
    //  See https://github.com/jakartaee/transactions/pull/222
    private static <T> T callInOurTx(RunOptionsBase options, Callable<T> task) {
        begin(options);
        boolean readOnly = options != null && options.readOnly;
        try {
            T ret;
            try {
                ret = task.call();
            } catch (Throwable t) {
                TransactionExceptionResult handling = TransactionExceptionResult.ROLLBACK;
                if (options.exceptionHandler != null) {
                    handling = options.exceptionHandler.apply(t);
                }
                if (readOnly || handling == TransactionExceptionResult.ROLLBACK) {
                    getUserTransaction().rollback();
                } else {
                    getUserTransaction().commit();
                }
                if (t instanceof RuntimeException) {
                    throw (RuntimeException) t;
                } else {
                    throw new QuarkusTransactionException(t);
                }
            }
            try {
                if (readOnly) {
                    getUserTransaction().rollback();
                } else {
                    getUserTransaction().commit();
                }
            } catch (Throwable t) {
                throw new QuarkusTransactionException(t);
            }
            return ret;
        } catch (SystemException | RollbackException | HeuristicMixedException | HeuristicRollbackException t) {
            try {
                getUserTransaction().rollback();
            } catch (Throwable e) {
                t.addSuppressed(e);
            }
            throw new QuarkusTransactionException(t);
        }
    }

    // TODO once Narayana implements Jakarta Transactions read-only, replace
    //  ReadOnlyTransactionSynchronization.markReadOnly(...) with the standard API.
    //  See https://github.com/jakartaee/transactions/pull/222
    private static <T> T callInTheirTx(RunOptionsBase options, Callable<T> task) {
        if (options != null && options.readOnly) {
            throw new QuarkusTransactionException(
                    "Cannot mark a joining transaction as read-only. Use requiresNew() to start a new read-only transaction.");
        }
        try {
            T ret;
            try {
                ret = task.call();
            } catch (Throwable t) {
                TransactionExceptionResult handling = TransactionExceptionResult.ROLLBACK;
                if (options.exceptionHandler != null) {
                    handling = options.exceptionHandler.apply(t);
                }
                if (handling == TransactionExceptionResult.ROLLBACK) {
                    getUserTransaction().setRollbackOnly();
                }
                if (t instanceof RuntimeException) {
                    throw (RuntimeException) t;
                } else {
                    throw new QuarkusTransactionException(t);
                }
            }
            return ret;
        } catch (SystemException t) {
            try {
                getUserTransaction().rollback();
            } catch (Throwable e) {
                t.addSuppressed(e);
            }
            throw new QuarkusTransactionException(t);
        }
    }

    private static void begin(RunOptionsBase options) {
        int timeout = options != null ? options.timeout : 0;
        boolean readOnly = options != null && options.readOnly;
        try {
            if (timeout > 0) {
                getUserTransaction().setTransactionTimeout(timeout);
            }
            getUserTransaction().begin();
        } catch (NotSupportedException | SystemException e) {
            throw new QuarkusTransactionException(e);
        } finally {
            if (timeout > 0) {
                try {
                    getUserTransaction().setTransactionTimeout(
                            (int) Arc.container().instance(TransactionManagerConfiguration.class)
                                    .get().defaultTransactionTimeout().toSeconds());
                } catch (SystemException e) {
                    log.error("Failed to reset transaction timeout", e);
                }
            }
        }
        // TODO once Narayana implements Jakarta Transactions read-only, replace with
        //  tm.setReadOnly(true) called BEFORE begin().
        //  See https://github.com/jakartaee/transactions/pull/222
        if (readOnly) {
            ReadOnlyTransactionSynchronization.markReadOnly(
                    Arc.container().instance(TransactionSynchronizationRegistry.class).get());
        }
    }

    static void begin(BeginOptions options) {
        RequestScopedTransaction tx = Arc.container().instance(RequestScopedTransaction.class).get();
        tx.begin(options);
    }

    static void rollback() {
        try {
            getUserTransaction().rollback();
        } catch (SystemException e) {
            throw new QuarkusTransactionException(e);
        }
    }

    // TODO once Narayana implements Jakarta Transactions read-only, replace
    //  ReadOnlyTransactionSynchronization.isReadOnly(...) with tsr.isReadOnly().
    //  See https://github.com/jakartaee/transactions/pull/222
    static void commit() {
        try {
            if (ReadOnlyTransactionSynchronization
                    .isReadOnly(Arc.container().instance(TransactionSynchronizationRegistry.class).get())) {
                getUserTransaction().rollback();
            } else {
                getUserTransaction().commit();
            }
        } catch (SystemException | RollbackException | HeuristicMixedException | HeuristicRollbackException e) {
            throw new QuarkusTransactionException(e);
        }
    }

    static void setRollbackOnly() {
        try {
            getUserTransaction().setRollbackOnly();
        } catch (SystemException e) {
            throw new RuntimeException(e);
        }
    }

    private static jakarta.transaction.UserTransaction getUserTransaction() {
        if (cachedUserTransaction == null) {
            return cachedUserTransaction = Arc.container().instance(UserTransaction.class).get();
        }
        return cachedUserTransaction;
    }

    private static TransactionManager getTransactionManager() {
        if (cachedTransactionManager == null) {
            return cachedTransactionManager = Arc.container().instance(TransactionManager.class).get();
        }
        return cachedTransactionManager;
    }
}
