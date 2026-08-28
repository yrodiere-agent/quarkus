package io.quarkus.spring.tx.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.TransactionSynchronizationRegistry;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.transaction.annotation.Transactional;

import io.quarkus.narayana.jta.runtime.ReadOnlyTransactionSynchronization;
import io.quarkus.test.QuarkusExtensionTest;

public class SpringTransactionalReadOnlyTest {

    @RegisterExtension
    static final QuarkusExtensionTest TEST = new QuarkusExtensionTest().setArchiveProducer(
            () -> ShrinkWrap.create(JavaArchive.class).addClass(ReadOnlyBean.class));

    @Inject
    ReadOnlyBean bean;

    @Test
    public void testReadOnlyTransaction() {
        assertThat(bean.isReadOnly()).isTrue();
    }

    @Test
    public void testNonReadOnlyTransaction() {
        assertThat(bean.isNotReadOnly()).isFalse();
    }

    @ApplicationScoped
    static class ReadOnlyBean {

        @Inject
        TransactionSynchronizationRegistry tsr;

        @Transactional(readOnly = true)
        public boolean isReadOnly() {
            return ReadOnlyTransactionSynchronization.isReadOnly(tsr);
        }

        @Transactional
        public boolean isNotReadOnly() {
            return ReadOnlyTransactionSynchronization.isReadOnly(tsr);
        }
    }
}
