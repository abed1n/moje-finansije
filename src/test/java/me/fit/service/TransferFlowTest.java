package me.fit.service;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import me.fit.dto.*;
import me.fit.model.Account;
import me.fit.model.AccountType;
import me.fit.model.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class TransferFlowTest {

    @Inject
    TransferService transferService;

    @Inject
    AuthService authService;

    @Inject
    AccountService accountService;

    @Inject
    EntityManager em;

    @Test
    @Transactional
    void transfer_pomjera_novac_a_ponistavanje_ga_vraca() {
        String email = "tr-" + System.nanoTime() + "@pfm.me";
        authService.register(new RegisterRequest("Transfer Test", email, "lozinka123"));
        User user = em.createNamedQuery(User.GET_BY_EMAIL, User.class)
                .setParameter("email", email)
                .getSingleResult();

        AccountDto from = accountService.createAccount(user, new AccountRequest(
                "Tekući", AccountType.CHECKING, "EUR", new BigDecimal("500.00"), null));
        AccountDto to = accountService.createAccount(user, new AccountRequest(
                "Štednja", AccountType.SAVINGS, "EUR", BigDecimal.ZERO, null));

        TransferDto transfer = transferService.createTransfer(user, new TransferRequest(
                new BigDecimal("150.00"), LocalDate.now(), "Test prebacivanje", from.id(), to.id()));

        em.flush();
        em.clear();
        assertEquals(0, em.find(Account.class, from.id()).getBalance().compareTo(new BigDecimal("350.00")));
        assertEquals(0, em.find(Account.class, to.id()).getBalance().compareTo(new BigDecimal("150.00")));
        assertEquals(1, transferService.getTransfers(user, 20).size());

        // Isti racun i razlicite valute su zabranjeni
        assertThrows(BadRequestException.class, () -> transferService.createTransfer(user,
                new TransferRequest(BigDecimal.TEN, LocalDate.now(), null, from.id(), from.id())));
        AccountDto usd = accountService.createAccount(user, new AccountRequest(
                "Dolarski", AccountType.CHECKING, "USD", BigDecimal.ZERO, null));
        assertThrows(BadRequestException.class, () -> transferService.createTransfer(user,
                new TransferRequest(BigDecimal.TEN, LocalDate.now(), null, from.id(), usd.id())));

        transferService.deleteTransfer(user, transfer.id());
        em.flush();
        em.clear();
        assertEquals(0, em.find(Account.class, from.id()).getBalance().compareTo(new BigDecimal("500.00")));
        assertEquals(0, em.find(Account.class, to.id()).getBalance().compareTo(BigDecimal.ZERO));
        assertTrue(transferService.getTransfers(user, 20).isEmpty());
    }
}
