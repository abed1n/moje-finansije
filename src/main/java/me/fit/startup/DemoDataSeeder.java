package me.fit.startup;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import me.fit.dto.*;
import me.fit.model.AccountType;
import me.fit.model.BudgetPeriod;
import me.fit.model.TransactionType;
import me.fit.model.User;
import me.fit.service.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Puni praznu bazu demo podacima kad je app.seed-demo-data=true
// (nalozi: demo@pfm.me / demo123 i admin@pfm.me / admin123)
@ApplicationScoped
public class DemoDataSeeder {

    private static final Logger LOG = Logger.getLogger(DemoDataSeeder.class);

    @ConfigProperty(name = "app.seed-demo-data", defaultValue = "false")
    boolean seedEnabled;

    @Inject
    EntityManager em;

    @Inject
    AuthService authService;

    @Inject
    UserService userService;

    @Inject
    AccountService accountService;

    @Inject
    CategoryService categoryService;

    @Inject
    TransactionService transactionService;

    @Inject
    BudgetService budgetService;

    void onStart(@Observes StartupEvent event) {
        if (!seedEnabled) {
            return;
        }
        Long userCount = em.createQuery("select count(u) from User u", Long.class).getSingleResult();
        if (userCount > 0) {
            LOG.info("Demo podaci se preskaču - baza već sadrži korisnike");
            return;
        }
        seed();
        LOG.info("Demo podaci ubačeni: demo@pfm.me / demo123, admin@pfm.me / admin123");
    }

    private void seed() {
        authService.register(new RegisterRequest("Admin", "admin@pfm.me", "admin123"));
        userService.promoteToAdmin("admin@pfm.me");

        authService.register(new RegisterRequest("Demo Korisnik", "demo@pfm.me", "demo123"));
        User demo = em.createNamedQuery(User.GET_BY_EMAIL, User.class)
                .setParameter("email", "demo@pfm.me")
                .getSingleResult();

        AccountDto checking = accountService.createAccount(demo, new AccountRequest(
                "Tekući račun", AccountType.CHECKING, "EUR", BigDecimal.ZERO,
                new AccountDetailsDto("510-0022-1234-5678", "CKB banka", LocalDate.now().minusYears(3))));
        AccountDto savings = accountService.createAccount(demo, new AccountRequest(
                "Štednja", AccountType.SAVINGS, "EUR", new BigDecimal("1500.00"),
                new AccountDetailsDto("510-0022-8765-4321", "CKB banka", LocalDate.now().minusYears(1))));
        AccountDto cash = accountService.createAccount(demo, new AccountRequest(
                "Gotovina", AccountType.CASH, "EUR", new BigDecimal("80.00"), null));

        Map<String, CategoryDto> categories = categoryService.getCategories(demo).stream()
                .collect(Collectors.toMap(CategoryDto::name, category -> category));

        LocalDate today = LocalDate.now();
        YearMonth currentMonth = YearMonth.now();
        for (int back = 5; back >= 0; back--) {
            YearMonth month = currentMonth.minusMonths(back);
            addIfPast(demo, today, month.atDay(1), new BigDecimal(2400 + (back * 37) % 180),
                    TransactionType.INCOME, "Mjesečna plata", checking.id(), categories.get("Plata"), List.of("posao"));
            if (back % 2 == 0) {
                addIfPast(demo, today, month.atDay(14), new BigDecimal("350.00"),
                        TransactionType.INCOME, "Freelance projekat", checking.id(), categories.get("Honorar"), List.of("posao"));
            }
            addIfPast(demo, today, month.atDay(2), new BigDecimal("200.00"),
                    TransactionType.INCOME, "Mjesečna štednja", savings.id(), categories.get("Ostali prihodi"), List.of());

            addIfPast(demo, today, month.atDay(3), new BigDecimal("650.00"),
                    TransactionType.EXPENSE, "Kirija za stan", checking.id(), categories.get("Stanovanje"), List.of());
            addIfPast(demo, today, month.atDay(5), new BigDecimal(95 + (back * 11) % 40),
                    TransactionType.EXPENSE, "Velika kupovina namirnica", checking.id(), categories.get("Hrana"), List.of("porodica"));
            addIfPast(demo, today, month.atDay(12), new BigDecimal(60 + (back * 7) % 30),
                    TransactionType.EXPENSE, "Namirnice", checking.id(), categories.get("Hrana"), List.of());
            addIfPast(demo, today, month.atDay(20), new BigDecimal(45 + (back * 5) % 25),
                    TransactionType.EXPENSE, "Pekara i sitnice", cash.id(), categories.get("Hrana"), List.of());
            addIfPast(demo, today, month.atDay(8), new BigDecimal("60.00"),
                    TransactionType.EXPENSE, "Mjesečna karta za prevoz", checking.id(), categories.get("Prevoz"), List.of());
            addIfPast(demo, today, month.atDay(10), new BigDecimal(110 + (back * 9) % 35),
                    TransactionType.EXPENSE, "Struja, voda i internet", checking.id(), categories.get("Računi i režije"), List.of("režije"));
            if (back != 1) {
                addIfPast(demo, today, month.atDay(15), new BigDecimal(40 + (back * 13) % 30),
                        TransactionType.EXPENSE, "Kino i izlazak", cash.id(), categories.get("Zabava"), List.of("vikend"));
            }
            if (back % 3 == 0) {
                addIfPast(demo, today, month.atDay(18), new BigDecimal("32.50"),
                        TransactionType.EXPENSE, "Apoteka", checking.id(), categories.get("Zdravlje"), List.of());
            }
            addIfPast(demo, today, month.atDay(22), new BigDecimal(70 + (back * 17) % 60),
                    TransactionType.EXPENSE, "Odjeća i obuća", checking.id(), categories.get("Kupovina"), List.of());
        }

        budgetService.createBudget(demo, new BudgetRequest("Hrana mjesečno", new BigDecimal("450.00"),
                BudgetPeriod.MONTHLY, List.of(categories.get("Hrana").id())));
        budgetService.createBudget(demo, new BudgetRequest("Zabava i kupovina", new BigDecimal("250.00"),
                BudgetPeriod.MONTHLY, List.of(categories.get("Zabava").id(), categories.get("Kupovina").id())));
        budgetService.createBudget(demo, new BudgetRequest("Svi troškovi", new BigDecimal("2000.00"),
                BudgetPeriod.MONTHLY, List.of()));
    }

    private void addIfPast(User user, LocalDate today, LocalDate date, BigDecimal amount,
                           TransactionType type, String description, Long accountId,
                           CategoryDto category, List<String> tags) {
        if (date.isAfter(today)) {
            return;
        }
        transactionService.createTransaction(user, new TransactionRequest(
                amount, date, type, description, accountId,
                category != null ? category.id() : null, tags));
    }
}
