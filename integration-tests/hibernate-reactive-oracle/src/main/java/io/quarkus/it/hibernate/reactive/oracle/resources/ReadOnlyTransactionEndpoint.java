package io.quarkus.it.hibernate.reactive.oracle.resources;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.hibernate.reactive.mutiny.Mutiny;

import io.quarkus.it.hibernate.reactive.oracle.model.GuineaPig;
import io.quarkus.security.Authenticated;
import io.quarkus.transaction.annotations.ReadOnly;
import io.smallrye.mutiny.Uni;

@Path("/read-only-test")
@Produces(MediaType.TEXT_PLAIN)
@Authenticated
public class ReadOnlyTransactionEndpoint {

    @Inject
    Mutiny.Session session;

    @Inject
    Mutiny.SessionFactory sessionFactory;

    @GET
    public Uni<String> test() {
        // Setup: create one guinea pig
        return sessionFactory.withTransaction(s -> {
            // Clean up first
            return s.createQuery("DELETE FROM GuineaPig g WHERE g.name LIKE :prefix")
                    .setParameter("prefix", "readonly-test-%")
                    .executeUpdate()
                    .chain(() -> s.persist(new GuineaPig(9000, "readonly-test-initial")));
        })
                // Test 1: Read in a read-only transaction
                .chain(() -> readOnly())
                .chain(name -> {
                    if (!"readonly-test-initial".equals(name)) {
                        return Uni.createFrom().item("ERROR: expected 'readonly-test-initial', got '" + name + "'");
                    }
                    // Test 2: Write in read-only transaction (should be rolled back)
                    return writeInReadOnly()
                            .chain(() -> sessionFactory.withSession(s -> s.find(GuineaPig.class, 9001)))
                            .map(pig -> {
                                if (pig != null) {
                                    return "ERROR: write in read-only was not rolled back";
                                }
                                return "OK";
                            });
                });
    }

    @Transactional
    @ReadOnly
    public Uni<String> readOnly() {
        return session.find(GuineaPig.class, 9000)
                .map(GuineaPig::getName);
    }

    @Transactional
    @ReadOnly
    public Uni<Void> writeInReadOnly() {
        return session.persist(new GuineaPig(9001, "readonly-test-should-not-persist"))
                .replaceWithVoid();
    }
}
