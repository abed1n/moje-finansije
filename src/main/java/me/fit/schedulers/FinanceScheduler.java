package me.fit.schedulers;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import me.fit.model.Account;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.util.List;

@ApplicationScoped
public class FinanceScheduler {

    private static final Logger LOG = Logger.getLogger(FinanceScheduler.class);

    @Inject
    EntityManager em;

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
