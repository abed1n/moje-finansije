package me.fit.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import me.fit.dto.AccountDto;
import me.fit.dto.AccountRequest;
import me.fit.dto.ReconcileResultDto;
import me.fit.dto.TransactionRequest;
import me.fit.model.Account;
import me.fit.model.TransactionType;
import me.fit.model.User;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@ApplicationScoped
public class AccountService {

    @Inject
    EntityManager em;

    @Inject
    TransactionService transactionService;

    public List<AccountDto> getAccounts(User user) {
        return em.createNamedQuery(Account.GET_ACCOUNTS_BY_USER_ID, Account.class)
                .setParameter("id", user.getId())
                .getResultList()
                .stream()
                .map(AccountDto::from)
                .toList();
    }

    public AccountDto getAccount(User user, Long id) {
        return AccountDto.from(findOwned(user, id));
    }

    @Transactional
    public AccountDto createAccount(User user, AccountRequest request) {
        Account account = new Account();
        account.setUser(em.getReference(User.class, user.getId()));
        account.setBalance(request.initialBalance() != null ? request.initialBalance() : BigDecimal.ZERO);
        applyRequest(account, request);
        em.persist(account);
        return AccountDto.from(account);
    }

    @Transactional
    public AccountDto updateAccount(User user, Long id, AccountRequest request) {
        Account account = findOwned(user, id);
        applyRequest(account, request);
        return AccountDto.from(account);
    }

    @Transactional
    public void deleteAccount(User user, Long id) {
        Account account = findOwned(user, id);
        em.remove(account);
    }

    // Uskladjivanje sa stvarnim stanjem: razlika (zaboravljeni kes, provizija, kamata...)
    // se knjizi kao jedna transakcija pa se aplikacija i banka garantovano poklapaju
    @Transactional
    public ReconcileResultDto reconcile(User user, Long id, BigDecimal actualBalance) {
        Account account = findOwned(user, id);
        BigDecimal difference = actualBalance.subtract(account.getBalance());
        if (difference.signum() == 0) {
            return new ReconcileResultDto(false, BigDecimal.ZERO, account.getBalance());
        }
        transactionService.createTransaction(user, new TransactionRequest(
                difference.abs(), LocalDate.now(),
                difference.signum() > 0 ? TransactionType.INCOME : TransactionType.EXPENSE,
                "Usklađivanje stanja", id, null, null));
        return new ReconcileResultDto(true, difference, actualBalance);
    }

    public Account findOwned(User user, Long id) {
        Account account = em.find(Account.class, id);
        if (account == null) {
            throw new NotFoundException("Račun sa id " + id + " ne postoji");
        }
        if (!account.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("Nemate pristup ovom računu");
        }
        return account;
    }

    private void applyRequest(Account account, AccountRequest request) {
        account.setName(request.name().trim());
        account.setType(request.type());
        account.setCurrency(request.currency().toUpperCase());
        if (request.details() != null) {
            if (account.getAccountDetails() == null) {
                account.setAccountDetails(request.details().toEntity());
            } else {
                account.getAccountDetails().setAccountNumber(request.details().accountNumber());
                account.getAccountDetails().setBankName(request.details().bankName());
                account.getAccountDetails().setOpenedDate(request.details().openedDate());
            }
        }
    }
}
