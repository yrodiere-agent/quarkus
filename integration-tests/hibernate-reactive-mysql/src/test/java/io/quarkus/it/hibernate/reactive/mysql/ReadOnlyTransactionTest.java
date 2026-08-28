package io.quarkus.it.hibernate.reactive.mysql;

import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
public class ReadOnlyTransactionTest {

    @Test
    public void testReadOnlyTransaction() {
        RestAssured.when()
                .get("/read-only-test")
                .then()
                .statusCode(200)
                .body(is("OK"));
    }
}
