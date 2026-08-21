package io.quarkus.narayana.quarkus;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.runtime.util.ExceptionUtil;
import io.quarkus.test.QuarkusExtensionTest;
import io.quarkus.transaction.annotations.ReadOnly;

public class ReadOnlyOnClassWithoutTransactionalTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = new QuarkusExtensionTest()
            .withApplicationRoot((jar) -> jar
                    .addClasses(InvalidService.class))
            .assertException(t -> {
                Throwable rootCause = ExceptionUtil.getRootCause(t);
                if (rootCause instanceof IllegalStateException) {
                    String message = rootCause.getMessage();
                    assertTrue(message.contains("@ReadOnly"), "Should mention @ReadOnly: " + message);
                    assertTrue(message.contains("@Transactional"), "Should mention @Transactional: " + message);
                    assertTrue(message.contains("InvalidService"), "Should mention the class: " + message);
                } else {
                    fail("Expected IllegalStateException, got: " + rootCause, t);
                }
            });

    @Test
    public void test() {
        fail("Application was supposed to fail.");
    }

    @ReadOnly
    public static class InvalidService {
        public void readStuff() {
        }
    }
}
