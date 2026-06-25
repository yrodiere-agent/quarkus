package io.quarkus.narayana.jta.runtime;

import jakarta.transaction.TransactionSynchronizationRegistry;

/**
 * Utility for storing and retrieving the "read-only transaction" flag in the
 * {@link TransactionSynchronizationRegistry}, scoped to the current JTA transaction.
 * <p>
 * This is a temporary workaround until Narayana implements the Jakarta Transactions read-only API.
 * Once available, {@link #markReadOnly(TransactionSynchronizationRegistry)} should be replaced by
 * {@code TransactionManager.setReadOnly(true)} (called before {@code begin()}),
 * and {@link #isReadOnly(TransactionSynchronizationRegistry)} should be replaced by
 * {@code TransactionSynchronizationRegistry.isReadOnly()} or {@code Transaction.isReadOnly()}.
 * See https://github.com/jakartaee/transactions/pull/222
 */
public final class ReadOnlyTransactionSynchronization {

    private static final Object TSR_KEY = new Object();

    private ReadOnlyTransactionSynchronization() {
    }

    /**
     * Marks the current JTA transaction as read-only by storing a flag in the
     * {@link TransactionSynchronizationRegistry}.
     * <p>
     * Must be called while a transaction is active.
     *
     * @param tsr the transaction synchronization registry
     */
    public static void markReadOnly(TransactionSynchronizationRegistry tsr) {
        tsr.putResource(TSR_KEY, Boolean.TRUE);
    }

    /**
     * Checks whether the current JTA transaction has been marked as read-only.
     *
     * @param tsr the transaction synchronization registry
     * @return {@code true} if the current transaction is read-only, {@code false} otherwise
     *         (including when no transaction is active)
     */
    public static boolean isReadOnly(TransactionSynchronizationRegistry tsr) {
        try {
            return Boolean.TRUE.equals(tsr.getResource(TSR_KEY));
        } catch (IllegalStateException e) {
            return false;
        }
    }
}
