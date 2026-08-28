package io.quarkus.it.jpa.postgresql;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.quarkus.it.jpa.postgresql.defaultpu.Person;
import io.quarkus.narayana.jta.QuarkusTransaction;

@Path("/jpa-withxml/read-only-test")
@Produces(MediaType.TEXT_PLAIN)
public class ReadOnlyTransactionEndpoint {

    private static final String NAME_PREFIX = "readonly-test-";

    @Inject
    EntityManager em;

    @GET
    public String test() {
        // Setup: clean up any previous test data, then create one person
        QuarkusTransaction.requiringNew().run(() -> {
            em.createQuery("DELETE FROM Person p WHERE p.name LIKE :prefix")
                    .setParameter("prefix", NAME_PREFIX + "%")
                    .executeUpdate();
        });
        QuarkusTransaction.requiringNew().run(() -> {
            Person person = new Person();
            person.setName(NAME_PREFIX + "initial");
            em.persist(person);
        });

        // Test 1: Read in a read-only transaction
        long count = QuarkusTransaction.requiringNew().readOnly().call(() -> {
            return em.createQuery("SELECT COUNT(p) FROM Person p WHERE p.name LIKE :prefix", Long.class)
                    .setParameter("prefix", NAME_PREFIX + "%")
                    .getSingleResult();
        });
        if (count != 1) {
            return "ERROR: expected 1 person, got " + count;
        }

        // Test 2: Write in a read-only transaction without explicit flush
        //         The entity should be silently discarded (rolled back)
        QuarkusTransaction.requiringNew().readOnly().run(() -> {
            Person person = new Person();
            person.setName(NAME_PREFIX + "should-not-persist");
            em.persist(person);
        });

        // Verify the write was rolled back
        long countAfterSilentWrite = QuarkusTransaction.requiringNew().call(() -> {
            return em.createQuery("SELECT COUNT(p) FROM Person p WHERE p.name LIKE :prefix", Long.class)
                    .setParameter("prefix", NAME_PREFIX + "%")
                    .getSingleResult();
        });
        if (countAfterSilentWrite != 1) {
            return "ERROR: silent write was not rolled back, count=" + countAfterSilentWrite;
        }

        // Test 3: Write in a read-only transaction with explicit flush
        //         Behavior depends on the database: some reject the write, others allow it
        //         but roll it back when the transaction ends
        String writeWithFlushResult;
        try {
            QuarkusTransaction.requiringNew().readOnly().run(() -> {
                Person person = new Person();
                person.setName(NAME_PREFIX + "should-not-persist-flush");
                em.persist(person);
                em.flush();
            });
            writeWithFlushResult = "ROLLED_BACK";
        } catch (Exception e) {
            writeWithFlushResult = "EXCEPTION";
        }

        // Verify the entity was not persisted regardless of flush behavior
        long countAfterFlushWrite = QuarkusTransaction.requiringNew().call(() -> {
            return em.createQuery("SELECT COUNT(p) FROM Person p WHERE p.name LIKE :prefix", Long.class)
                    .setParameter("prefix", NAME_PREFIX + "%")
                    .getSingleResult();
        });
        if (countAfterFlushWrite != 1) {
            return "ERROR: flush write was not rolled back, count=" + countAfterFlushWrite;
        }

        return "OK:" + writeWithFlushResult;
    }
}
