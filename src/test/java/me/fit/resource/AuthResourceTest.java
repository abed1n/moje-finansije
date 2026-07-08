package me.fit.resource;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class AuthResourceTest {

    @Inject
    MockMailbox mailbox;

    @BeforeEach
    void clearMailbox() {
        mailbox.clear();
    }

    @Test
    void registerVerifyLoginAndMe() {
        String email = "auth-" + System.nanoTime() + "@pfm.me";

        // Registracija kreira neverifikovan nalog i ne vraca sesiju
        given().contentType("application/json")
                .body(Map.of("name", "Test Korisnik", "email", email, "password", "tajna123"))
                .when().post("/api/auth/register")
                .then().statusCode(201)
                .body("email", equalTo(email))
                .body("emailVerified", equalTo(false));

        // Prijava prije potvrde emaila je odbijena (403)
        given().contentType("application/json")
                .body(Map.of("email", email, "password", "tajna123"))
                .when().post("/api/auth/login")
                .then().statusCode(403);

        // Potvrda emaila pa uspjesna prijava
        verify(verificationTokenFor(email));
        String token = login(email, "tajna123");

        given().header("Authorization", "Bearer " + token)
                .when().get("/api/auth/me")
                .then().statusCode(200)
                .body("email", equalTo(email))
                .body("emailVerified", equalTo(true));

        // Dupli email -> 409
        given().contentType("application/json")
                .body(Map.of("name", "Duplikat", "email", email, "password", "tajna123"))
                .when().post("/api/auth/register")
                .then().statusCode(409);

        // Pogresna lozinka -> 401
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
    void registerValidationFails() {
        given().contentType("application/json")
                .body(Map.of("name", "", "email", "nije-email", "password", "123"))
                .when().post("/api/auth/register")
                .then().statusCode(400);
    }

    @Test
    void registerSendsVerificationEmailAndTokenIsSingleUse() {
        String email = "verify-" + System.nanoTime() + "@pfm.me";

        given().contentType("application/json")
                .body(Map.of("name", "Verify", "email", email, "password", "tajna123"))
                .when().post("/api/auth/register")
                .then().statusCode(201);

        // Tacno jedan verifikacioni email
        List<Mail> sent = mailbox.getMailsSentTo(email);
        assertEquals(1, sent.size(), "Očekivali smo verifikacioni email");

        String verifyToken = verificationTokenFor(email);
        verify(verifyToken);

        // Ponovna upotreba istog tokena je odbijena
        given().contentType("application/json")
                .body(Map.of("token", verifyToken))
                .when().post("/api/auth/verify-email")
                .then().statusCode(400);
    }

    @Test
    void refreshRotatesTokenAndLogoutRevokes() {
        String email = "refresh-" + System.nanoTime() + "@pfm.me";
        registerAndVerify("Refresh", email, "tajna123");

        // Prijava izdaje refresh kolacic
        String firstCookie = given().contentType("application/json")
                .body(Map.of("email", email, "password", "tajna123"))
                .when().post("/api/auth/login")
                .then().statusCode(200)
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
    void forgotPasswordSendsEmailAndResetWorks() {
        String email = "reset-" + System.nanoTime() + "@pfm.me";
        registerAndVerify("Reset", email, "staraLozinka");

        // Odbaci verifikacioni email da ostane samo onaj za reset
        mailbox.clear();

        given().contentType("application/json")
                .body(Map.of("email", email))
                .when().post("/api/auth/forgot-password")
                .then().statusCode(204);

        List<Mail> sent = mailbox.getMailsSentTo(email);
        assertEquals(1, sent.size(), "Očekivali smo tačno jedan email za reset");
        String text = sent.getFirst().getText();
        int idx = text.indexOf("?reset=");
        assertTrue(idx > 0, "Email mora sadržati link za reset");
        String token = text.substring(idx + "?reset=".length(), idx + "?reset=".length() + 64);

        given().contentType("application/json")
                .body(Map.of("token", token, "newPassword", "novaLozinka"))
                .when().post("/api/auth/reset-password")
                .then().statusCode(204);

        // Stara lozinka vise ne radi, nova radi (nalog je vec verifikovan)
        given().contentType("application/json")
                .body(Map.of("email", email, "password", "staraLozinka"))
                .when().post("/api/auth/login")
                .then().statusCode(401);

        given().contentType("application/json")
                .body(Map.of("email", email, "password", "novaLozinka"))
                .when().post("/api/auth/login")
                .then().statusCode(200);

        // Token je jednokratan
        given().contentType("application/json")
                .body(Map.of("token", token, "newPassword", "trecaLozinka"))
                .when().post("/api/auth/reset-password")
                .then().statusCode(400);
    }

    @Test
    void forgotPasswordForUnknownEmailStillReturns204() {
        given().contentType("application/json")
                .body(Map.of("email", "nepostoji-" + System.nanoTime() + "@pfm.me"))
                .when().post("/api/auth/forgot-password")
                .then().statusCode(204);
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

        // Sesti pokusaj je blokiran - 429, i to cak sa tacnom lozinkom
        given().contentType("application/json")
                .body(Map.of("email", email, "password", "tajna123"))
                .when().post("/api/auth/login")
                .then().statusCode(429)
                .header("Retry-After", notNullValue());
    }

    // --- pomocne metode ---

    private void register(String name, String email, String password) {
        given().contentType("application/json")
                .body(Map.of("name", name, "email", email, "password", password))
                .when().post("/api/auth/register")
                .then().statusCode(201);
    }

    private String verificationTokenFor(String email) {
        List<Mail> mails = mailbox.getMailsSentTo(email);
        assertTrue(!mails.isEmpty(), "Nema poslatog verifikacionog emaila za " + email);
        String text = mails.getLast().getText();
        int idx = text.indexOf("?verify=");
        assertTrue(idx > 0, "Email mora sadržati link za potvrdu");
        return text.substring(idx + "?verify=".length(), idx + "?verify=".length() + 64);
    }

    private void verify(String token) {
        given().contentType("application/json")
                .body(Map.of("token", token))
                .when().post("/api/auth/verify-email")
                .then().statusCode(204);
    }

    private void registerAndVerify(String name, String email, String password) {
        register(name, email, password);
        verify(verificationTokenFor(email));
    }

    private String login(String email, String password) {
        return given().contentType("application/json")
                .body(Map.of("email", email, "password", password))
                .when().post("/api/auth/login")
                .then().statusCode(200)
                .extract().path("token");
    }
}
