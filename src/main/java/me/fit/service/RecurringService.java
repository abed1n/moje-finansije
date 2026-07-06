package me.fit.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import me.fit.dto.RecurringDto;
import me.fit.dto.RecurringRequest;
import me.fit.dto.TransactionRequest;
import me.fit.model.Account;
import me.fit.model.Category;
import me.fit.model.RecurringTransaction;
import me.fit.model.User;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

// Ponavljajuca pravila: jednom mjesecno automatski upisu transakciju (kirija, plata...)
@ApplicationScoped
public class RecurringService {

    private static final Logger LOG = Logger.getLogger(RecurringService.class);

    @Inject
    EntityManager em;

    @Inject
    AccountService accountService;

    @Inject
    CategoryService categoryService;

    @Inject
    TransactionService transactionService;

    @Transactional
    public List<RecurringDto> getRules(User user) {
        return em.createNamedQuery(RecurringTransaction.GET_BY_USER_ID, RecurringTransaction.class)
                .setParameter("id", user.getId())
                .getResultList()
                .stream().map(RecurringDto::from).toList();
    }

    @Transactional
    public RecurringDto createRule(User user, RecurringRequest request) {
        Account account = accountService.findOwned(user, request.accountId());

        RecurringTransaction rule = new RecurringTransaction();
        rule.setAmount(request.amount());
        rule.setType(request.type());
        rule.setDescription(request.description());
        rule.setDayOfMonth(request.dayOfMonth());
        rule.setAccount(account);
        rule.setUser(em.getReference(User.class, user.getId()));

        if (request.categoryId() != null) {
            Category category = categoryService.findOwned(user, request.categoryId());
            if (category.getType() != request.type()) {
                throw new BadRequestException("Tip kategorije se ne poklapa sa tipom transakcije");
            }
            rule.setCategory(category);
        }

        // Ako je dan vec prosao ovog mjeseca, pravilo krece od sljedeceg mjeseca
        // (transakciju za tekuci mjesec korisnik upravo unosi rucno)
        YearMonth now = YearMonth.now();
        if (LocalDate.now().getDayOfMonth() >= effectiveDay(rule, now)) {
            rule.setLastRun(now.toString());
        }

        em.persist(rule);
        return RecurringDto.from(rule);
    }

    @Transactional
    public RecurringDto toggleRule(User user, Long id) {
        RecurringTransaction rule = findOwned(user, id);
        rule.setActive(!rule.isActive());
        return RecurringDto.from(rule);
    }

    @Transactional
    public void deleteRule(User user, Long id) {
        em.remove(findOwned(user, id));
    }

    // Poziva je scheduler: kreira transakcije za sva pravila koja su dospjela ovog mjeseca
    @Transactional
    public int generateDue() {
        LocalDate today = LocalDate.now();
        YearMonth currentMonth = YearMonth.from(today);
        int created = 0;

        List<RecurringTransaction> rules = em
                .createNamedQuery(RecurringTransaction.GET_ACTIVE, RecurringTransaction.class)
                .getResultList();

        for (RecurringTransaction rule : rules) {
            int dueDay = effectiveDay(rule, currentMonth);
            boolean alreadyRan = currentMonth.toString().equals(rule.getLastRun());
            if (alreadyRan || today.getDayOfMonth() < dueDay) {
                continue;
            }
            try {
                TransactionRequest request = new TransactionRequest(
                        rule.getAmount(),
                        currentMonth.atDay(dueDay),
                        rule.getType(),
                        rule.getDescription(),
                        rule.getAccount().getId(),
                        rule.getCategory() != null ? rule.getCategory().getId() : null,
                        null);
                transactionService.createTransaction(rule.getUser(), request);
                rule.setLastRun(currentMonth.toString());
                created++;
            } catch (RuntimeException e) {
                LOG.errorf("Ponavljajuce pravilo %d nije izvrseno: %s", rule.getId(), e.getMessage());
            }
        }
        return created;
    }

    // Dan izvrsenja u konkretnom mjesecu: 31. u junu znaci 30. jun
    private int effectiveDay(RecurringTransaction rule, YearMonth month) {
        return Math.min(rule.getDayOfMonth(), month.lengthOfMonth());
    }

    private RecurringTransaction findOwned(User user, Long id) {
        RecurringTransaction rule = em.find(RecurringTransaction.class, id);
        if (rule == null) {
            throw new NotFoundException("Ponavljajuće pravilo sa id " + id + " ne postoji");
        }
        if (!rule.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("Nemate pristup ovom pravilu");
        }
        return rule;
    }
}
