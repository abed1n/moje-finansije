package me.fit.schedulers;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import me.fit.model.Account;
import me.fit.service.RecurringService;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.util.List;

@ApplicationScoped
public class FinanceScheduler {

    private static final Logger LOG = Logger.getLogger(FinanceScheduler.class);

    @Inject
    EntityManager em;

    @Inject
    RecurringService recurringService;

    // Ponavljajuca pravila: provjera odmah po startu pa svakog sata
    @Scheduled(every = "1h", delayed = "20s")
    public void generateRecurringTransactions() {
        int created = recurringService.generateDue();
        if (created > 0) {
            LOG.infof("Kreirano %d transakcija iz ponavljajućih pravila", created);
        }
    }

    @Scheduled(every = "10m")
    @Transactional
    public void checkNegativeBalance() {
        List<Account> accounts = em.createQuery(
                        "select a from Account a where a.balance < :zero", Account.class)
                .setParameter("zero", BigDecimal.ZERO)
                .getResultList();
        for (Account account : accounts) {
            LOG.warnf("Račun '%s' (korisnik %s) ima negativan balans: %s",
                    account.getName(), account.getUser().getEmail(), account.getBalance());
        }
    }
}
