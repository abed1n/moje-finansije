package me.fit.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;
import me.fit.dto.AttachmentDto;
import me.fit.dto.TransactionDto;
import me.fit.dto.TransactionRequest;
import me.fit.model.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class TransactionService {

    @Inject
    EntityManager em;

    @Inject
    AccountService accountService;

    @Inject
    CategoryService categoryService;

    @ConfigProperty(name = "app.uploads.dir", defaultValue = "uploads")
    String uploadsDir;

    @Transactional
    public List<TransactionDto> getTransactions(User user, Long accountId, Long categoryId,
                                                TransactionType type, LocalDate from, LocalDate to,
                                                String search, int limit) {
        StringBuilder jpql = new StringBuilder(
                "select t from Transaction t where t.account.user.id = :userId");
        Map<String, Object> params = new HashMap<>();
        params.put("userId", user.getId());

        if (accountId != null) {
            jpql.append(" and t.account.id = :accountId");
            params.put("accountId", accountId);
        }
        if (categoryId != null) {
            jpql.append(" and t.category.id = :categoryId");
            params.put("categoryId", categoryId);
        }
        if (type != null) {
            jpql.append(" and t.type = :type");
            params.put("type", type);
        }
        if (from != null) {
            jpql.append(" and t.date >= :fromDate");
            params.put("fromDate", from);
        }
        if (to != null) {
            jpql.append(" and t.date <= :toDate");
            params.put("toDate", to);
        }
        if (search != null && !search.isBlank()) {
            jpql.append(" and lower(t.description) like :search");
            params.put("search", "%" + search.trim().toLowerCase() + "%");
        }
        jpql.append(" order by t.date desc, t.id desc");

        TypedQuery<Transaction> query = em.createQuery(jpql.toString(), Transaction.class);
        params.forEach(query::setParameter);
        query.setMaxResults(limit > 0 ? limit : 200);

        return query.getResultList().stream().map(TransactionDto::from).toList();
    }

    @Transactional
    public TransactionDto getTransaction(User user, Long id) {
        return TransactionDto.from(findOwned(user, id));
    }

    @Transactional
    public TransactionDto createTransaction(User user, TransactionRequest request) {
        Account account = accountService.findOwned(user, request.accountId());
        Transaction transaction = new Transaction();
        transaction.setAccount(account);
        applyRequest(user, transaction, request);
        account.setBalance(account.getBalance().add(transaction.signedAmount()));
        em.persist(transaction);
        return TransactionDto.from(transaction);
    }

    @Transactional
    public TransactionDto updateTransaction(User user, Long id, TransactionRequest request) {
        Transaction transaction = findOwned(user, id);

        Account oldAccount = transaction.getAccount();
        oldAccount.setBalance(oldAccount.getBalance().subtract(transaction.signedAmount()));

        Account newAccount = accountService.findOwned(user, request.accountId());
        transaction.setAccount(newAccount);
        applyRequest(user, transaction, request);
        newAccount.setBalance(newAccount.getBalance().add(transaction.signedAmount()));

        return TransactionDto.from(transaction);
    }

    @Transactional
    public void deleteTransaction(User user, Long id) {
        Transaction transaction = findOwned(user, id);
        Account account = transaction.getAccount();
        account.setBalance(account.getBalance().subtract(transaction.signedAmount()));
        for (UploadedFile file : transaction.getUploadedFiles()) {
            deleteStoredFile(file);
        }
        em.remove(transaction);
    }

    @Transactional
    public AttachmentDto addAttachment(User user, Long transactionId, FileUpload upload) {
        if (upload == null) {
            throw new BadRequestException("Fajl je obavezan");
        }
        Transaction transaction = findOwned(user, transactionId);

        String originalName = sanitizeFilename(upload.fileName());
        String storedName = UUID.randomUUID() + "-" + originalName;
        try {
            Path dir = Paths.get(uploadsDir);
            Files.createDirectories(dir);
            Path destination = dir.resolve(storedName);
            Files.copy(upload.uploadedFile(), destination);

            UploadedFile file = new UploadedFile();
            file.setFilename(originalName);
            file.setStoredPath(destination.toString());
            file.setContentType(upload.contentType());
            file.setSize(upload.size());
            file.setTransaction(transaction);
            transaction.getUploadedFiles().add(file);
            em.persist(file);
            return AttachmentDto.from(file);
        } catch (IOException e) {
            throw new InternalServerErrorException("Snimanje fajla nije uspjelo", e);
        }
    }

    @Transactional
    public UploadedFile getAttachment(User user, Long attachmentId) {
        UploadedFile file = em.find(UploadedFile.class, attachmentId);
        if (file == null) {
            throw new NotFoundException("Prilog sa id " + attachmentId + " ne postoji");
        }
        if (!file.getTransaction().getAccount().getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("Nemate pristup ovom prilogu");
        }
        return file;
    }

    @Transactional
    public void deleteAttachment(User user, Long attachmentId) {
        UploadedFile file = getAttachment(user, attachmentId);
        deleteStoredFile(file);
        file.getTransaction().getUploadedFiles().remove(file);
        em.remove(file);
    }

    private Transaction findOwned(User user, Long id) {
        Transaction transaction = em.find(Transaction.class, id);
        if (transaction == null) {
            throw new NotFoundException("Transakcija sa id " + id + " ne postoji");
        }
        if (!transaction.getAccount().getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("Nemate pristup ovoj transakciji");
        }
        return transaction;
    }

    private void applyRequest(User user, Transaction transaction, TransactionRequest request) {
        transaction.setAmount(request.amount());
        transaction.setDate(request.date());
        transaction.setType(request.type());
        transaction.setDescription(request.description());

        if (request.categoryId() != null) {
            Category category = categoryService.findOwned(user, request.categoryId());
            if (category.getType() != request.type()) {
                throw new BadRequestException("Tip kategorije se ne poklapa sa tipom transakcije");
            }
            transaction.setCategory(category);
        } else {
            transaction.setCategory(null);
        }

        transaction.setTags(resolveTags(user, request.tags()));
    }

    private List<Tag> resolveTags(User user, List<String> names) {
        List<Tag> tags = new ArrayList<>();
        if (names == null) {
            return tags;
        }
        for (String rawName : names) {
            if (rawName == null || rawName.isBlank()) {
                continue;
            }
            String name = rawName.trim();
            List<Tag> existing = em.createNamedQuery(Tag.GET_TAG_BY_USER_AND_NAME, Tag.class)
                    .setParameter("id", user.getId())
                    .setParameter("name", name)
                    .getResultList();
            if (existing.isEmpty()) {
                Tag tag = new Tag();
                tag.setName(name);
                tag.setUser(em.getReference(User.class, user.getId()));
                em.persist(tag);
                tags.add(tag);
            } else if (tags.stream().noneMatch(t -> t.getId().equals(existing.getFirst().getId()))) {
                tags.add(existing.getFirst());
            }
        }
        return tags;
    }

    private void deleteStoredFile(UploadedFile file) {
        try {
            Files.deleteIfExists(Paths.get(file.getStoredPath()));
        } catch (IOException e) {
            throw new UncheckedIOException("Brisanje fajla " + file.getStoredPath() + " nije uspjelo", e);
        }
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "prilog";
        }
        String name = Paths.get(filename).getFileName().toString();
        return name.replaceAll("[^\\w.\\-]", "_");
    }

    public BigDecimal sumAmount(User user, TransactionType type, LocalDate from, LocalDate to, List<Category> categories) {
        StringBuilder jpql = new StringBuilder(
                "select coalesce(sum(t.amount), 0) from Transaction t"
                        + " where t.account.user.id = :userId and t.type = :type"
                        + " and t.date >= :fromDate and t.date <= :toDate");
        if (categories != null && !categories.isEmpty()) {
            jpql.append(" and t.category in :categories");
        }
        TypedQuery<BigDecimal> query = em.createQuery(jpql.toString(), BigDecimal.class)
                .setParameter("userId", user.getId())
                .setParameter("type", type)
                .setParameter("fromDate", from)
                .setParameter("toDate", to);
        if (categories != null && !categories.isEmpty()) {
            query.setParameter("categories", categories);
        }
        return query.getSingleResult();
    }
}
