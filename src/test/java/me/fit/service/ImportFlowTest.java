package me.fit.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import me.fit.dto.*;
import me.fit.model.AccountType;
import me.fit.model.Category;
import me.fit.model.TransactionType;
import me.fit.model.User;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ImportFlowTest {

    private static final DateTimeFormatter BANK_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    @Inject
    ImportService importService;

    @Inject
    AuthService authService;

    @Inject
    AccountService accountService;

    @Inject
    TransactionService transactionService;

    @Inject
    EntityManager em;

    @Test
    @Transactional
    void uvoz_kategorise_preskace_duplikate_i_uci_pravila() {
        String email = "imp-" + System.nanoTime() + "@pfm.me";
        authService.register(new RegisterRequest("Import Test", email, "lozinka123"));
        User user = em.createNamedQuery(User.GET_BY_EMAIL, User.class)
                .setParameter("email", email)
                .getSingleResult();
        AccountDto account = accountService.createAccount(user, new AccountRequest(
                "Tekući", AccountType.CHECKING, "EUR", BigDecimal.ZERO, null));

        LocalDate day1 = LocalDate.now().minusDays(4);
        LocalDate day2 = LocalDate.now().minusDays(2);

        // Postojeca transakcija — red iz izvoda sa istim datumom/iznosom mora biti duplikat
        transactionService.createTransaction(user, new TransactionRequest(
                new BigDecimal("45.50"), day1, TransactionType.EXPENSE, "Ranije uneseno",
                account.id(), null, null));

        String csv = "Datum;Opis;Iznos\n"
                + day1.format(BANK_DATE) + ";VOLI 7 PODGORICA;-45,50\n"
                + day2.format(BANK_DATE) + ";ZARADA JUL;1.200,00\n"
                + day2.format(BANK_DATE) + ";NEPOZNATO XYZ;-10,00\n";

        ImportPreviewDto preview = importService.preview(user, account.id(),
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));

        assertEquals(3, preview.rows().size());
        assertTrue(preview.skipped().isEmpty());

        ImportRowDto voli = preview.rows().get(0);
        assertEquals(TransactionType.EXPENSE, voli.type());
        assertEquals(0, voli.amount().compareTo(new BigDecimal("45.50")));
        assertEquals("Hrana", voli.suggestedCategoryName(), "VOLI mora biti prepoznat kao Hrana");
        assertTrue(voli.duplicate(), "Postojeca transakcija mora biti oznacena kao duplikat");

        ImportRowDto plata = preview.rows().get(1);
        assertEquals(TransactionType.INCOME, plata.type());
        assertEquals(0, plata.amount().compareTo(new BigDecimal("1200.00")),
                "1.200,00 mora biti parsiran kao 1200 (tacka za hiljade, zarez decimalni)");
        assertEquals("Plata", plata.suggestedCategoryName());

        ImportRowDto nepoznato = preview.rows().get(2);
        assertNull(nepoznato.suggestedCategoryId(), "Nepoznat opis nema prijedlog");

        // Uvoz nepoznatog reda uz rucno izabranu kategoriju -> pravilo se uci
        Category zabava = em.createQuery(
                        "select c from Category c where c.user.id = :id and c.name = 'Zabava'", Category.class)
                .setParameter("id", user.getId())
                .getSingleResult();

        ImportResultDto result = importService.confirmImport(user, new ImportConfirmRequest(
                account.id(), true, List.of(new ImportConfirmRequest.Row(
                        day2, "NEPOZNATO XYZ", new BigDecimal("10.00"),
                        TransactionType.EXPENSE, zabava.getId()))));
        assertEquals(1, result.created());
        assertEquals(1, result.rulesLearned());

        // Drugi uvoz istog izvoda: nauceno pravilo predlaze Zabavu, red je sada i duplikat
        ImportPreviewDto second = importService.preview(user, account.id(),
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
        ImportRowDto learned = second.rows().get(2);
        assertEquals("Zabava", learned.suggestedCategoryName(), "Nauceno pravilo mora da vazi");
        assertTrue(learned.duplicate());
    }
}
