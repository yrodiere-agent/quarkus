package io.quarkus.hibernate.orm.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.hibernate.FlushMode;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusExtensionTest;
import io.quarkus.transaction.annotations.ReadOnly;

public class ReadOnlyTransactionTest {

    @RegisterExtension
    static QuarkusExtensionTest runner = new QuarkusExtensionTest()
            .withApplicationRoot((jar) -> jar
                    .addClass(SimpleEntity.class)
                    .addClass(ReadOnlyBean.class)
                    .addClass(WriterBean.class)
                    .addAsResource("application.properties"));

    @Inject
    ReadOnlyBean readOnlyBean;

    @Inject
    WriterBean writerBean;

    @Test
    public void sessionIsDefaultReadOnlyInReadOnlyTransaction() {
        readOnlyBean.assertSessionIsReadOnly();
    }

    @Test
    public void flushModeIsManualInReadOnlyTransaction() {
        readOnlyBean.assertFlushModeIsManual();
    }

    @Test
    public void readOnlyTransactionCanReadData() {
        long id = writerBean.createEntity("test-read");
        String name = readOnlyBean.readEntityName(id);
        assertThat(name).isEqualTo("test-read");
    }

    @Test
    public void writeInReadOnlyTransactionIsRolledBack() {
        readOnlyBean.persistEntity(42L, "should-not-persist");
        assertThat(writerBean.findEntityName(42L)).isNull();
    }

    @Test
    public void subsequentWriteTransactionWorksNormally() {
        readOnlyBean.assertSessionIsReadOnly();
        long id = writerBean.createEntity("after-readonly");
        assertThat(writerBean.findEntityName(id)).isEqualTo("after-readonly");
    }

    @ReadOnly
    @ApplicationScoped
    static class ReadOnlyBean {
        @Inject
        EntityManager entityManager;

        @Transactional
        public void assertSessionIsReadOnly() {
            Session session = entityManager.unwrap(Session.class);
            assertThat(session.isDefaultReadOnly()).isTrue();
        }

        @Transactional
        public void assertFlushModeIsManual() {
            Session session = entityManager.unwrap(Session.class);
            assertThat(session.getHibernateFlushMode()).isEqualTo(FlushMode.MANUAL);
        }

        @Transactional
        public String readEntityName(long id) {
            SimpleEntity entity = entityManager.find(SimpleEntity.class, id);
            return entity != null ? entity.getName() : null;
        }

        @Transactional
        public void persistEntity(long id, String name) {
            SimpleEntity entity = new SimpleEntity(name);
            entity.setId(id);
            entityManager.persist(entity);
        }
    }

    @ApplicationScoped
    static class WriterBean {
        @Inject
        EntityManager entityManager;

        @Transactional
        public long createEntity(String name) {
            SimpleEntity entity = new SimpleEntity(name);
            entity.setId(System.nanoTime());
            entityManager.persist(entity);
            return entity.getId();
        }

        @Transactional
        public String findEntityName(long id) {
            SimpleEntity entity = entityManager.find(SimpleEntity.class, id);
            return entity != null ? entity.getName() : null;
        }
    }
}
