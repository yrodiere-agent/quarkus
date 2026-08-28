package io.quarkus.it.jpa.mssql;

import static org.hamcrest.Matchers.startsWith;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;

@QuarkusTest
public class ReadOnlyTransactionTest {

    @Test
    public void testReadOnlyTransaction() {
        RestAssured.when().get("/jpa-mssql/read-only-test").then().body(startsWith("OK"));
    }
}
