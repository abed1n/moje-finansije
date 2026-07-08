package me.fit.resource;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class FinanceFlowTest {

    @Inject
    MockMailbox mailbox;

    // Registruje korisnika, potvrdi email iz mock poruke i prijavi se za pristupni token
    private String registerAndGetToken() {
        String email = "flow-" + System.nanoTime() + "@pfm.me";
        String password = "tajna123";
        given().contentType(ContentType.JSON)
                .body(Map.of("name", "Flow Test", "email", email, "password", password))
                .when().post("/api/auth/register")
                .then().statusCode(201);

        List<Mail> mails = mailbox.getMailsSentTo(email);
        String text = mails.getLast().getText();
        int idx = text.indexOf("?verify=");
        String verifyToken = text.substring(idx + "?verify=".length(), idx + "?verify=".length() + 64);
        given().contentType(ContentType.JSON)
                .body(Map.of("token", verifyToken))
                .when().post("/api/auth/verify-email")
                .then().statusCode(204);

        return given().contentType(ContentType.JSON)
                .body(Map.of("email", email, "password", password))
                .when().post("/api/auth/login")
                .then().statusCode(200)
                .extract().path("token");
    }

    private RequestSpecification as(String token) {
        return given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON);
    }

    @Test
    void fullFinanceFlow() {
        String token = registerAndGetToken();

        // registracija automatski kreira default kategorije
        List<Map<String, Object>> categories = as(token)
                .when().get("/api/categories")
                .then().statusCode(200)
                .body("size()", greaterThan(5))
                .extract().jsonPath().getList("$");
        long expenseCategoryId = ((Number) categories.stream()
                .filter(c -> "EXPENSE".equals(c.get("type"))).findFirst().orElseThrow().get("id")).longValue();
        long incomeCategoryId = ((Number) categories.stream()
                .filter(c -> "INCOME".equals(c.get("type"))).findFirst().orElseThrow().get("id")).longValue();

        // kreiranje racuna sa pocetnim stanjem
        long accountId = ((Number) as(token)
                .body(Map.of("name", "Testni račun", "type", "CHECKING", "currency", "EUR",
                        "initialBalance", 100.00))
                .when().post("/api/accounts")
                .then().statusCode(201)
                .body("balance", equalTo(100.00f))
                .extract().path("id")).longValue();

        String today = LocalDate.now().toString();

        // prihod od 1000 -> balans 1100
        as(token).body(transaction(1000.00, today, "INCOME", "Plata", accountId, incomeCategoryId, List.of("posao")))
                .when().post("/api/transactions")
                .then().statusCode(201)
                .body("tags", hasItem("posao"));
        as(token).when().get("/api/accounts/" + accountId)
                .then().statusCode(200).body("balance", equalTo(1100.00f));

        // rashod od 250.50 -> balans 849.50
        long expenseTxId = ((Number) as(token)
                .body(transaction(250.50, today, "EXPENSE", "Namirnice", accountId, expenseCategoryId, List.of()))
                .when().post("/api/transactions")
                .then().statusCode(201)
                .extract().path("id")).longValue();
        as(token).when().get("/api/accounts/" + accountId)
                .then().statusCode(200).body("balance", equalTo(849.50f));

        // filter po tipu
        as(token).when().get("/api/transactions?type=EXPENSE")
                .then().statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].description", equalTo("Namirnice"));

        // tip kategorije mora odgovarati tipu transakcije
        as(token).body(transaction(10.00, today, "INCOME", "Pogrešno", accountId, expenseCategoryId, List.of()))
                .when().post("/api/transactions")
                .then().statusCode(400);

        // budzet nad expense kategorijom: potroseno 250.50 od 501 -> 50%
        as(token).body(Map.of("name", "Test budžet", "limitAmount", 501.00,
                        "period", "MONTHLY", "categoryIds", List.of(expenseCategoryId)))
                .when().post("/api/budgets")
                .then().statusCode(201)
                .body("spent", equalTo(250.50f))
                .body("percentUsed", equalTo(50));

        // dashboard
        as(token).when().get("/api/dashboard")
                .then().statusCode(200)
                .body("totalBalance", equalTo(849.50f))
                .body("incomeThisMonth", equalTo(1000.00f))
                .body("expenseThisMonth", equalTo(250.50f))
                .body("accountCount", equalTo(1))
                .body("monthlyFlow.size()", equalTo(6))
                .body("recentTransactions.size()", equalTo(2));

        // kategorija u upotrebi se ne moze obrisati
        as(token).when().delete("/api/categories/" + expenseCategoryId)
                .then().statusCode(409);

        // brisanje transakcije vraca balans
        as(token).when().delete("/api/transactions/" + expenseTxId)
                .then().statusCode(204);
        as(token).when().get("/api/accounts/" + accountId)
                .then().statusCode(200).body("balance", equalTo(1100.00f));

        // drugi korisnik nema pristup tudjem racunu
        String otherToken = registerAndGetToken();
        as(otherToken).when().get("/api/accounts/" + accountId)
                .then().statusCode(403);

        // admin endpoint zabranjen obicnom korisniku
        as(token).when().get("/api/admin/users")
                .then().statusCode(403);
    }

    private Map<String, Object> transaction(double amount, String date, String type, String description,
                                            long accountId, Long categoryId, List<String> tags) {
        Map<String, Object> body = new HashMap<>();
        body.put("amount", amount);
        body.put("date", date);
        body.put("type", type);
        body.put("description", description);
        body.put("accountId", accountId);
        body.put("categoryId", categoryId);
        body.put("tags", tags);
        return body;
    }
}
