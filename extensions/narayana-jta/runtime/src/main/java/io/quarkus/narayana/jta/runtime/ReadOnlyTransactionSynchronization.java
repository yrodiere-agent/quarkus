package io.quarkus.narayana.jta.runtime;

import java.util.HashSet;
import java.util.Set;

import javax.transaction.xa.XAResource;

import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionSynchronizationRegistry;

import org.jboss.tm.XAResourceWrapper;

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
    private static final Object ENFORCING_RESOURCES_KEY = new Object();

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

    /**
     * Records that a resource from the given datasource enforces read-only mode at the database level.
     * <p>
     * Called by pool interceptors when a connection is acquired in a read-only transaction
     * from a datasource whose JDBC driver enforces {@code Connection.setReadOnly(true)}.
     *
     * @param tsr the transaction synchronization registry
     * @param dataSourceName the datasource name, must match the value passed as the "jndiName" parameter
     *        to {@code NarayanaTransactionIntegration} during pool configuration
     */
    @SuppressWarnings("unchecked")
    public static void addReadOnlyEnforcingResource(TransactionSynchronizationRegistry tsr, String dataSourceName) {
        Set<String> enforcingResources = (Set<String>) tsr.getResource(ENFORCING_RESOURCES_KEY);
        if (enforcingResources == null) {
            enforcingResources = new HashSet<>();
            tsr.putResource(ENFORCING_RESOURCES_KEY, enforcingResources);
        }
        enforcingResources.add(dataSourceName);
    }

    /**
     * Determines whether a read-only transaction should commit rather than roll back.
     * <p>
     * Returns {@code true} only if the transaction is read-only and <em>every</em> enlisted XA resource
     * is an Agroal resource whose datasource enforces read-only mode. If any resource is non-Agroal
     * or from a non-enforcing datasource, returns {@code false} (roll back as a safety net).
     *
     * @param tsr the transaction synchronization registry
     * @param tx the current transaction
     * @return {@code true} if the read-only transaction should commit
     */
    @SuppressWarnings("unchecked")
    public static boolean shouldCommitReadOnly(TransactionSynchronizationRegistry tsr, Transaction tx) {
        if (!isReadOnly(tsr)) {
            return false;
        }

        Set<String> enforcingResources = (Set<String>) tsr.getResource(ENFORCING_RESOURCES_KEY);

        if (!(tx instanceof com.arjuna.ats.jta.transaction.Transaction narayanaTx)) {
            return false;
        }
        Set<XAResource> resources = narayanaTx.getResources().keySet();
        if (resources.isEmpty()) {
            return false;
        }

        for (XAResource xaResource : resources) {
            if (!(xaResource instanceof XAResourceWrapper)) {
                return false;
            }
            // getJndiName() returns the datasource name set during pool configuration
            String jndiName = ((XAResourceWrapper) xaResource).getJndiName();
            if (enforcingResources == null || !enforcingResources.contains(jndiName)) {
                return false;
            }
        }
        return true;
    }
}
