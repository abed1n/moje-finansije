package me.fit.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import me.fit.dto.CategoryDto;
import me.fit.dto.CategoryRequest;
import me.fit.exception.ConflictException;
import me.fit.model.Category;
import me.fit.model.TransactionType;
import me.fit.model.User;

import java.util.List;

@ApplicationScoped
public class CategoryService {

    @Inject
    EntityManager em;

    public List<CategoryDto> getCategories(User user) {
        return em.createNamedQuery(Category.GET_CATEGORIES_BY_USER_ID, Category.class)
                .setParameter("id", user.getId())
                .getResultList()
                .stream()
                .map(CategoryDto::from)
                .toList();
    }

    @Transactional
    public CategoryDto createCategory(User user, CategoryRequest request) {
        Category category = new Category();
        category.setUser(em.getReference(User.class, user.getId()));
        applyRequest(category, request);
        em.persist(category);
        return CategoryDto.from(category);
    }

    @Transactional
    public CategoryDto updateCategory(User user, Long id, CategoryRequest request) {
        Category category = findOwned(user, id);
        applyRequest(category, request);
        return CategoryDto.from(category);
    }

    @Transactional
    public void deleteCategory(User user, Long id) {
        Category category = findOwned(user, id);
        Long transactionCount = em.createQuery(
                        "select count(t) from Transaction t where t.category.id = :id", Long.class)
                .setParameter("id", id)
                .getSingleResult();
        if (transactionCount > 0) {
            throw new ConflictException("Kategorija se koristi na " + transactionCount
                    + " transakcija i ne može se obrisati");
        }
        category.getBudgets().forEach(budget -> budget.getCategories().remove(category));
        em.remove(category);
    }

    @Transactional
    public void seedDefaultCategories(User user) {
        record Seed(String name, TransactionType type, String color, String icon) {
        }
        // Boje su iz kategorijske palete validirane za CVD (daltonizam) i kontrast;
        // redoslijed slotova je dio validacije pa ga ne mijenjati proizvoljno
        List<Seed> seeds = List.of(
                new Seed("Hrana", TransactionType.EXPENSE, "#2a78d6", "🍔"),
                new Seed("Stanovanje", TransactionType.EXPENSE, "#1baf7a", "🏠"),
                new Seed("Prevoz", TransactionType.EXPENSE, "#eda100", "🚗"),
                new Seed("Računi i režije", TransactionType.EXPENSE, "#008300", "💡"),
                new Seed("Zabava", TransactionType.EXPENSE, "#4a3aa7", "🎬"),
                new Seed("Zdravlje", TransactionType.EXPENSE, "#e34948", "💊"),
                new Seed("Kupovina", TransactionType.EXPENSE, "#e87ba4", "🛍️"),
                new Seed("Ostali troškovi", TransactionType.EXPENSE, "#eb6834", "📦"),
                new Seed("Plata", TransactionType.INCOME, "#2a78d6", "💼"),
                new Seed("Honorar", TransactionType.INCOME, "#1baf7a", "🧾"),
                new Seed("Pokloni", TransactionType.INCOME, "#eda100", "🎁"),
                new Seed("Ostali prihodi", TransactionType.INCOME, "#4a3aa7", "💰"));
        for (Seed seed : seeds) {
            Category category = new Category();
            category.setUser(user);
            category.setName(seed.name());
            category.setType(seed.type());
            category.setColor(seed.color());
            category.setIcon(seed.icon());
            em.persist(category);
        }
    }

    public Category findOwned(User user, Long id) {
        Category category = em.find(Category.class, id);
        if (category == null) {
            throw new NotFoundException("Kategorija sa id " + id + " ne postoji");
        }
        if (!category.getUser().getId().equals(user.getId())) {
            throw new ForbiddenException("Nemate pristup ovoj kategoriji");
        }
        return category;
    }

    private void applyRequest(Category category, CategoryRequest request) {
        category.setName(request.name().trim());
        category.setType(request.type());
        category.setColor(request.color());
        category.setIcon(request.icon());
    }
}
