package io.quarkus.it.main;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.h2.H2DatabaseTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@QuarkusTestResource(value = H2DatabaseTestResource.class, restrictToAnnotatedClass = true)
@TestProfile(H2DatabaseTestResourceTestCase.Profile.class)
public class H2DatabaseTestResourceTestCase {

    public static class Profile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.datasource.jdbc.url",
                    "jdbc:h2:tcp://localhost/mem:h2_test_resource_compat");
        }
    }

    @Test
    public void testAppUsesH2FromTestResource() {
        given().get("/datasource")
                .then().statusCode(200);

        String url = given().get("/datasource/url")
                .then().statusCode(200)
                .extract().asString();
        assertThat(url).contains("mem:h2_test_resource_compat");
        assertThat(url).doesNotContain("mem:quarkus");
    }
}
