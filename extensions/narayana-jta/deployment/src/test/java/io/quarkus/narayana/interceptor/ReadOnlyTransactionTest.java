package io.quarkus.narayana.interceptor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.transaction.RollbackException;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.SystemException;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.transaction.Transactional;
import jakarta.transaction.UserTransaction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.narayana.jta.QuarkusTransactionException;
import io.quarkus.narayana.jta.runtime.ReadOnlyTransactionSynchronization;
import io.quarkus.test.QuarkusExtensionTest;
import io.quarkus.transaction.annotations.ReadOnly;

public class ReadOnlyTransactionTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = new QuarkusExtensionTest()
            .withApplicationRoot((jar) -> jar
                    .addClasses(ReadOnlyBean.class, NonReadOnlyBean.class,
                            TestXAResource.class, TestXAResourceWrapper.class, TxAssertionData.class));

    @Inject
    TransactionManager tm;

    @Inject
    UserTransaction userTransaction;

    @Inject
    ReadOnlyBean readOnlyBean;

    @Inject
    NonReadOnlyBean nonReadOnlyBean;

    @Inject
    TransactionSynchronizationRegistry tsr;

    @Inject
    TxAssertionData txAssertionData;

    @AfterEach
    public void tearDown() {
        try {
            userTransaction.rollback();
        } catch (Exception e) {
            // do nothing
        } finally {
            txAssertionData.reset();
        }
    }

    @Test
    public void readOnlyMethodRollsBack() throws Exception {
        readOnlyBean.doWork();
        Assertions.assertEquals(0, txAssertionData.getCommit());
        Assertions.assertEquals(1, txAssertionData.getRollback());
    }

    @Test
    public void readOnlyClassLevelRollsBack() throws Exception {
        readOnlyBean.doWorkInherited();
        Assertions.assertEquals(0, txAssertionData.getCommit());
        Assertions.assertEquals(1, txAssertionData.getRollback());
    }

    @Test
    public void tsrFlagIsSetDuringExecution() {
        readOnlyBean.assertReadOnlyFlagSet();
    }

    @Test
    public void readOnlyRejectedOnNestedTransaction() {
        RuntimeException e = Assertions.assertThrows(RuntimeException.class,
                () -> nonReadOnlyBean.callNestedReadOnly());
        Assertions.assertTrue(e.getMessage().contains("entry level"),
                "Expected message to contain 'entry level', got: " + e.getMessage());
    }

    @Test
    public void readOnlyMethodInsideProgrammaticTransactionIsRejected() {
        RuntimeException e = Assertions.assertThrows(RuntimeException.class,
                () -> QuarkusTransaction.requiringNew().run(() -> {
                    try {
                        readOnlyBean.doWork();
                    } catch (RuntimeException re) {
                        throw re;
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                }));
        Assertions.assertTrue(e.getMessage().contains("entry level"),
                "Expected message to contain 'entry level', got: " + e.getMessage());
    }

    @Test
    public void programmaticReadOnlyRequiringNewRollsBack() {
        var sync = new TestSync();
        QuarkusTransaction.requiringNew().readOnly().run(() -> register(sync));
        Assertions.assertEquals(Status.STATUS_ROLLEDBACK, sync.completionStatus);
    }

    @Test
    @ActivateRequestContext
    public void programmaticReadOnlyWithCommitOnRequestScopeEndThrows() {
        Assertions.assertThrows(QuarkusTransactionException.class,
                () -> QuarkusTransaction.begin(QuarkusTransaction.beginOptions().readOnly().commitOnRequestScopeEnd()));
    }

    @Test
    @ActivateRequestContext
    public void programmaticReadOnlyJoiningExistingInsideNonReadOnlyThrows() {
        Assertions.assertThrows(QuarkusTransactionException.class,
                () -> nonReadOnlyBean.runWithJoiningReadOnly());
    }

    @Test
    @ActivateRequestContext
    public void readOnlyRequiresNewInsideNonReadOnlyTransaction() {
        var outerSync = new TestSync();
        var innerSync = new TestSync();
        nonReadOnlyBean.runWithNestedRequiresNewReadOnly(outerSync, innerSync);
        Assertions.assertEquals(Status.STATUS_ROLLEDBACK, innerSync.completionStatus);
        Assertions.assertEquals(Status.STATUS_COMMITTED, outerSync.completionStatus);
    }

    @Test
    public void nonReadOnlyJoiningInsideReadOnlyIsRejected() {
        RuntimeException e = Assertions.assertThrows(RuntimeException.class,
                () -> readOnlyBean.callNonReadOnlyJoining());
        Assertions.assertTrue(e.getMessage().contains("non-read-only"),
                "Expected message to contain 'non-read-only', got: " + e.getMessage());
    }

    @Test
    public void programmaticNonReadOnlyJoiningInsideReadOnlyIsRejected() {
        Assertions.assertThrows(QuarkusTransactionException.class,
                () -> readOnlyBean.runWithProgrammaticNonReadOnlyJoining());
    }

    @Test
    public void declarativeRequiresNewReadOnlyRollsBack() throws Exception {
        readOnlyBean.doWorkRequiresNew();
        Assertions.assertEquals(0, txAssertionData.getCommit());
        Assertions.assertEquals(1, txAssertionData.getRollback());
    }

    @Test
    public void programmaticReadOnlyWithEnforcingResourceStillRollsBack() {
        var sync = new TestSync();
        QuarkusTransaction.requiringNew().readOnly().run(() -> {
            String dsName = "test-enforcing-ds";
            ReadOnlyTransactionSynchronization.addReadOnlyEnforcingResource(tsr, dsName);
            try {
                tm.getTransaction().enlistResource(new TestXAResourceWrapper(txAssertionData, dsName));
                tm.getTransaction().registerSynchronization(sync);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        Assertions.assertEquals(Status.STATUS_ROLLEDBACK, sync.completionStatus);
    }

    @Test
    public void readOnlyWithEnforcingResourceCommits() throws Exception {
        readOnlyBean.doWorkWithEnforcingResource();
        Assertions.assertEquals(1, txAssertionData.getCommit());
        Assertions.assertEquals(0, txAssertionData.getRollback());
    }

    @Test
    public void readOnlyWithNonEnforcingResourceRollsBack() throws Exception {
        readOnlyBean.doWork();
        Assertions.assertEquals(0, txAssertionData.getCommit());
        Assertions.assertEquals(1, txAssertionData.getRollback());
    }

    @Test
    public void readOnlyWithMixedResourcesRollsBack() throws Exception {
        readOnlyBean.doWorkWithMixedResources();
        Assertions.assertEquals(0, txAssertionData.getCommit());
        Assertions.assertEquals(2, txAssertionData.getRollback());
    }

    private void register(TestSync sync) {
        try {
            tm.getTransaction().registerSynchronization(sync);
        } catch (RollbackException | SystemException e) {
            throw new RuntimeException(e);
        }
    }

    static class TestSync implements Synchronization {
        int completionStatus = -1;

        @Override
        public void beforeCompletion() {
        }

        @Override
        public void afterCompletion(int status) {
            this.completionStatus = status;
        }
    }

    @ReadOnly
    @ApplicationScoped
    static class ReadOnlyBean {
        @Inject
        TransactionManager transactionManager;

        @Inject
        TransactionSynchronizationRegistry tsr;

        @Inject
        TxAssertionData txAssertionData;

        @Inject
        NonReadOnlyBean nonReadOnlyBean;

        @Transactional
        @ReadOnly
        public void doWork() throws Exception {
            transactionManager.getTransaction()
                    .enlistResource(new TestXAResource(txAssertionData));
        }

        @Transactional(Transactional.TxType.REQUIRES_NEW)
        @ReadOnly
        public void doWorkRequiresNew() throws Exception {
            transactionManager.getTransaction()
                    .enlistResource(new TestXAResource(txAssertionData));
        }

        @Transactional
        public void doWorkInherited() throws Exception {
            transactionManager.getTransaction()
                    .enlistResource(new TestXAResource(txAssertionData));
        }

        @Transactional
        @ReadOnly
        public void assertReadOnlyFlagSet() {
            Assertions.assertTrue(ReadOnlyTransactionSynchronization.isReadOnly(tsr));
        }

        @Transactional
        @ReadOnly
        public void doWorkWithEnforcingResource() throws Exception {
            String dsName = "test-enforcing-ds";
            ReadOnlyTransactionSynchronization.addReadOnlyEnforcingResource(tsr, dsName);
            transactionManager.getTransaction()
                    .enlistResource(new TestXAResourceWrapper(txAssertionData, dsName));
        }

        @Transactional
        @ReadOnly
        public void doWorkWithMixedResources() throws Exception {
            String dsName = "test-enforcing-ds";
            ReadOnlyTransactionSynchronization.addReadOnlyEnforcingResource(tsr, dsName);
            transactionManager.getTransaction()
                    .enlistResource(new TestXAResourceWrapper(txAssertionData, dsName));
            transactionManager.getTransaction()
                    .enlistResource(new TestXAResource(txAssertionData));
        }

        @Transactional
        @ReadOnly
        public void callNonReadOnlyJoining() throws Exception {
            transactionManager.getTransaction()
                    .enlistResource(new TestXAResource(txAssertionData));
            nonReadOnlyBean.joinExisting();
        }

        @Transactional
        @ReadOnly
        public void runWithProgrammaticNonReadOnlyJoining() {
            QuarkusTransaction.joiningExisting().run(() -> {
            });
        }
    }

    @ApplicationScoped
    static class NonReadOnlyBean {
        @Inject
        TransactionManager transactionManager;

        @Inject
        ReadOnlyBean readOnlyBean;

        @Inject
        TxAssertionData txAssertionData;

        @Transactional
        public void callNestedReadOnly() throws Exception {
            readOnlyBean.doWork();
        }

        @Transactional
        public void runWithJoiningReadOnly() {
            QuarkusTransaction.joiningExisting().readOnly().run(() -> {
            });
        }

        @Transactional
        public void runWithNestedRequiresNewReadOnly(TestSync outerSync, TestSync innerSync) {
            try {
                transactionManager.getTransaction().registerSynchronization(outerSync);
            } catch (RollbackException | SystemException e) {
                throw new RuntimeException(e);
            }
            QuarkusTransaction.requiringNew().readOnly().run(() -> {
                try {
                    transactionManager.getTransaction().registerSynchronization(innerSync);
                } catch (RollbackException | SystemException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        @Transactional
        public void joinExisting() {
        }
    }
}
