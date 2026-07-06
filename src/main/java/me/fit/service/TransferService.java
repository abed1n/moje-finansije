package me.fit.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import me.fit.dto.TransferDto;
import me.fit.dto.TransferRequest;
import me.fit.model.Account;
import me.fit.model.Transfer;
import me.fit.model.User;

import java.util.List;

// Prebacivanje novca izmedju vlastitih racuna: oba stanja se mijenjaju atomski,
// a iznos ne ulazi u prihode/rashode pa statistika ostaje tacna
@ApplicationScoped
public class TransferService {

    @Inject
    EntityManager em;

    @Inject
    AccountService accountService;

    @Transactional
    public List<TransferDto> getTransfers(User user, int limit) {
        return em.createNamedQuery(Transfer.GET_BY_USER_ID, Transfer.class)
                .setParameter("id", user.getId())
                .setMaxResults(limit > 0 ? limit : 20)
                .getResultList()
                .stream().map(TransferDto::from).toList();
    }

    @Transactional
    public TransferDto createTransfer(User user, TransferRequest request) {
        if (request.fromAccountId().equals(request.toAccountId())) {
            throw new BadRequestException("Izvorni i odredišni račun ne smiju biti isti");
        }
        Account from = accountService.findOwned(user, request.fromAccountId());
        Account to = accountService.findOwned(user, request.toAccountId());
        if (!from.getCurrency().equals(to.getCurrency())) {
            throw new BadRequestException("Računi moraju biti u istoj valuti ("
                    + from.getCurrency() + " → " + to.getCurrency() + ")");
        }

        Transfer transfer = new Transfer();
        transfer.setAmount(request.amount());
        transfer.setDate(request.date());
        transfer.setDescription(request.description());
        transfer.setFromAccount(from);
        transfer.setToAccount(to);
        transfer.setUser(em.getReference(User.class, user.getId()));

        from.setBalance(from.getBalance().subtract(request.amount()));
        to.setBalance(to.getBalance().add(request.amount()));

        em.persist(transfer);
        return TransferDto.from(transfer);
    }

    @Transactional
    public void deleteTransfer(User user, Long id) {
        Transfer transfer = findOwned(user, id);
        // Brisanje vraca novac na izvorni racun
        transfer.getFromAccount().setBalance(transfer.getFromAccount().getBalance().add(transfer.getAmount()));
        transfer.getToAccount().setBalance(transfer.getToAccount().getBalance().subtract(transfer.getAmount()));
        em.remove(transfer);
    }

    private Transfer findOwned(User user, Long id) {
        Transfer transfer = em.find(Transfer.class, id);
        if (transfer == null) {
            throw new NotFoundException("Prebacivanje sa id " + id + " ne postoji");
        }
        if (!transfer.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("Nemate pristup ovom prebacivanju");
        }
        return transfer;
    }
}
