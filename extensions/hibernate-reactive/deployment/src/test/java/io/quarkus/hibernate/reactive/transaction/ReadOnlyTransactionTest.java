package io.quarkus.hibernate.reactive.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.hibernate.FlushMode;
import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.reactive.transaction.runtime.TransactionalInterceptorRequired;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.quarkus.transaction.annotations.ReadOnly;
import io.smallrye.mutiny.Uni;

public class ReadOnlyTransactionTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar
                    .addClasses(Hero.class, ReadOnlyBean.class, WriterBean.class)
                    .addClasses(TransactionalInterceptorRequired.class)
                    .addAsResource("initialTransactionData.sql", "import.sql"))
            .withConfigurationResource("application-reactive-transaction.properties");

    @Inject
    ReadOnlyBean readOnlyBean;

    @Inject
    WriterBean writerBean;

    @Inject
    Mutiny.SessionFactory sessionFactory;

    @Test
    @RunOnVertxContext
    public void sessionIsDefaultReadOnlyInReadOnlyTransaction(UniAsserter asserter) {
        asserter.assertThat(
                () -> readOnlyBean.checkSessionIsDefaultReadOnly(),
                result -> assertThat(result).isTrue());
    }

    @Test
    @RunOnVertxContext
    public void readOnlyTransactionCanReadData(UniAsserter asserter) {
        asserter.assertThat(
                () -> readOnlyBean.findHeroName(50L),
                name -> assertThat(name).isEqualTo("initialName"));
    }

    @Test
    @RunOnVertxContext
    public void writeInReadOnlyTransactionIsRolledBack(UniAsserter asserter) {
        asserter.assertThat(
                () -> readOnlyBean.persistHero(999L, "should-not-persist")
                        .chain(() -> sessionFactory.withSession(s -> s.find(Hero.class, 999L))),
                hero -> assertThat(hero).isNull());
    }

    @Test
    @RunOnVertxContext
    public void flushModeIsManualInReadOnlyTransaction(UniAsserter asserter) {
        asserter.assertThat(
                () -> readOnlyBean.checkFlushModeIsManual(),
                result -> assertThat(result).isEqualTo(FlushMode.MANUAL));
    }

    @Test
    @RunOnVertxContext
    public void readOnlyClassLevelAppliesToAllMethods(UniAsserter asserter) {
        asserter.assertThat(
                () -> readOnlyBean.checkSessionIsDefaultReadOnlyInherited(),
                result -> assertThat(result).isTrue());
    }

    @ReadOnly
    @ApplicationScoped
    static class ReadOnlyBean {
        @Inject
        Mutiny.Session session;

        @Transactional
        @ReadOnly
        public Uni<Boolean> checkSessionIsDefaultReadOnly() {
            return session.find(Hero.class, 50L)
                    .map(h -> session.isDefaultReadOnly());
        }

        @Transactional
        @ReadOnly
        public Uni<FlushMode> checkFlushModeIsManual() {
            return session.find(Hero.class, 50L)
                    .map(h -> session.getFlushMode());
        }

        @Transactional
        public Uni<Boolean> checkSessionIsDefaultReadOnlyInherited() {
            return session.find(Hero.class, 50L)
                    .map(h -> session.isDefaultReadOnly());
        }

        @Transactional
        @ReadOnly
        public Uni<String> findHeroName(Long id) {
            return session.find(Hero.class, id)
                    .map(Hero::getName);
        }

        @Transactional
        @ReadOnly
        public Uni<Void> persistHero(Long id, String name) {
            Hero hero = new Hero(name);
            hero.id = id;
            return session.persist(hero).replaceWithVoid();
        }
    }

    @ApplicationScoped
    static class WriterBean {
        @Inject
        Mutiny.Session session;

        @Transactional
        public Uni<Long> createHero(String name) {
            Hero hero = new Hero(name);
            return session.persist(hero)
                    .map(v -> hero.id);
        }
    }
}
