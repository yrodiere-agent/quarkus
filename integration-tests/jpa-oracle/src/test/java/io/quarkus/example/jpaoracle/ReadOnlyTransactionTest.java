package io.quarkus.example.jpaoracle;

import static org.hamcrest.Matchers.startsWith;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
public class ReadOnlyTransactionTest {

    @Test
    public void testReadOnlyTransaction() {
        RestAssured.when().get("/jpa-oracle/read-only-test").then().body(startsWith("OK"));
    }
}
