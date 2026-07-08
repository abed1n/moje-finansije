package me.fit.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class AuthResourceTest {

    @Test
    void registerLoginAndMe() {
        String email = "auth-" + System.nanoTime() + "@pfm.me";

        String token = given()
                .contentType("application/json")
                .body(Map.of("name", "Test Korisnik", "email", email, "password", "tajna123"))
                .when().post("/api/auth/register")
                .then().statusCode(201)
                .body("token", notNullValue())
                .body("user.email", equalTo(email))
                .body("user.role", equalTo("USER"))
                .extract().path("token");

        given().header("Authorization", "Bearer " + token)
                .when().get("/api/auth/me")
                .then().statusCode(200)
                .body("email", equalTo(email));

        // dupli email -> 409
        given().contentType("application/json")
                .body(Map.of("name", "Duplikat", "email", email, "password", "tajna123"))
                .when().post("/api/auth/register")
                .then().statusCode(409);

        // login sa ispravnom lozinkom
        given().contentType("application/json")
                .body(Map.of("email", email, "password", "tajna123"))
                .when().post("/api/auth/login")
                .then().statusCode(200)
                .body("token", notNullValue());

        // login sa pogresnom lozinkom
        given().contentType("application/json")
                .body(Map.of("email", email, "password", "pogresna"))
                .when().post("/api/auth/login")
                .then().statusCode(401);
    }

    @Test
    void meWithoutTokenReturns401() {
        given().when().get("/api/auth/me").then().statusCode(401);
    }

    @Test
    void refreshRotatesTokenAndLogoutRevokes() {
        String email = "refresh-" + System.nanoTime() + "@pfm.me";

        String firstCookie = given()
                .contentType("application/json")
                .body(Map.of("name", "Refresh", "email", email, "password", "tajna123"))
                .when().post("/api/auth/register")
                .then().statusCode(201)
                .cookie("refresh_token", notNullValue())
                .extract().cookie("refresh_token");

        // Osvjezavanje vraca novi pristupni token i rotira refresh kolacic
        Response refreshed = given()
                .cookie("refresh_token", firstCookie)
                .when().post("/api/auth/refresh")
                .then().statusCode(200)
                .body("token", notNullValue())
                .cookie("refresh_token", notNullValue())
                .extract().response();

        String newToken = refreshed.path("token");
        String newCookie = refreshed.cookie("refresh_token");

        // Novi pristupni token je ispravan
        given().header("Authorization", "Bearer " + newToken)
                .when().get("/api/auth/me")
                .then().statusCode(200)
                .body("email", equalTo(email));

        // Stari refresh kolacic je ponisten rotacijom
        given().cookie("refresh_token", firstCookie)
                .when().post("/api/auth/refresh")
                .then().statusCode(401);

        // Odjava opoziva i vazeci refresh token
        given().cookie("refresh_token", newCookie)
                .when().post("/api/auth/logout")
                .then().statusCode(204);

        given().cookie("refresh_token", newCookie)
                .when().post("/api/auth/refresh")
                .then().statusCode(401);
    }

    @Test
    void tooManyFailedLoginsGetBlocked() {
        String email = "brute-" + System.nanoTime() + "@pfm.me";

        given().contentType("application/json")
                .body(Map.of("name", "Meta", "email", email, "password", "tajna123"))
                .when().post("/api/auth/register")
                .then().statusCode(201);

        // Pet pogresnih pokusaja jos uvijek vraca 401
        for (int i = 0; i < 5; i++) {
            given().contentType("application/json")
                    .body(Map.of("email", email, "password", "pogresna"))
                    .when().post("/api/auth/login")
                    .then().statusCode(401);
        }

        // Sesti pokusaj je blokiran - 429 sa Retry-After, i to cak sa tacnom lozinkom
        given().contentType("application/json")
                .body(Map.of("email", email, "password", "tajna123"))
                .when().post("/api/auth/login")
                .then().statusCode(429)
                .header("Retry-After", notNullValue());
    }

    @Test
    void registerValidationFails() {
        given().contentType("application/json")
                .body(Map.of("name", "", "email", "nije-email", "password", "123"))
                .when().post("/api/auth/register")
                .then().statusCode(400);
    }
}
