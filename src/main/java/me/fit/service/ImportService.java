package me.fit.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import me.fit.dto.*;
import me.fit.model.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

// Uvoz bankovnog izvoda (CSV) sa automatskom kategorizacijom.
// Parser podnosi razlicite formate: ; ili , ili tab separator, razliciti formati
// datuma, jedna kolona iznosa sa predznakom ili odvojene kolone uplata/isplata.
@ApplicationScoped
public class ImportService {

    private static final int MAX_ROWS = 500;

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("d.M.yyyy"),
            DateTimeFormatter.ofPattern("d.M.yyyy."),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("d-M-yyyy")
    };

    // Ugradjene kljucne rijeci -> naziv kategorije (poklapa se sa default setom korisnika)
    private static final Map<String, List<String>> BUILTIN_KEYWORDS = Map.of(
            "Hrana", List.of("VOLI", "IDEA", "AROMA", "MERCATOR", "KONZUM", "MEGA MARKET", "LAKOVIC",
                    "FRANCA", "GLOVO", "WOLT", "PEKARA", "MARKET", "RESTORAN", "DOMINO"),
            "Prevoz", List.of("TAXI", "AUTOBUS", "JUGOPETROL", "LUKOIL", "PETROL", "EKO ", "INA ",
                    "PARKING", "BENZIN", "GORIVO"),
            "Računi i režije", List.of("EPCG", "ELEKTRO", "VODOVOD", "CISTOCA", "TELEKOM", "TELENOR",
                    "MTEL", "M:TEL", "ONE ", "INTERNET", "KOMUNAL"),
            "Zdravlje", List.of("APOTEKA", "PHARMA", "MONTEFARM", "BENU", "POLIKLINIKA", "DOM ZDRAVLJA"),
            "Zabava", List.of("CINEPLEXX", "KINO", "BIOSKOP", "NETFLIX", "SPOTIFY", "STEAM", "HBO",
                    "PLAYSTATION"),
            "Stanovanje", List.of("KIRIJA", "STANARINA", "ZAKUP"),
            "Kupovina", List.of("ZARA", "H&M", "NEW YORKER", "LC WAIKIKI", "DEICHMANN", "TEHNOMAX",
                    "DATIKA", "AMAZON", "ALIEXPRESS", "TEMU"),
            "Plata", List.of("PLATA", "ZARADA", "LICNI DOHODAK"));

    @Inject
    EntityManager em;

    @Inject
    AccountService accountService;

    @Inject
    CategoryService categoryService;

    @Inject
    TransactionService transactionService;

    @Inject
    TransferService transferService;

    public record ParsedRow(int line, LocalDate date, String description, BigDecimal amount,
                            TransactionType type) {
    }

    @Transactional
    public ImportPreviewDto preview(User user, Long accountId, InputStream csv) {
        Account account = accountService.findOwned(user, accountId);

        List<String> skipped = new ArrayList<>();
        List<ParsedRow> parsed = parseCsv(csv, skipped);
        if (parsed.isEmpty()) {
            throw new BadRequestException("U fajlu nije pronađena nijedna transakcija. "
                    + "Očekuje se CSV izvod sa kolonama datum, opis i iznos.");
        }

        Set<String> existing = existingTransactionKeys(user, account, parsed);
        List<CategoryRule> userRules = em.createNamedQuery(CategoryRule.GET_BY_USER_ID, CategoryRule.class)
                .setParameter("id", user.getId())
                .getResultList();
        List<Category> categories = em.createQuery(
                        "select c from Category c where c.user.id = :id", Category.class)
                .setParameter("id", user.getId())
                .getResultList();

        List<ImportRowDto> rows = parsed.stream().map(row -> {
            Category suggested = suggestCategory(row, userRules, categories);
            boolean duplicate = existing.contains(transactionKey(row.date(), row.amount(), row.type()));
            return new ImportRowDto(row.line(), row.date(), row.description(), row.amount(), row.type(),
                    suggested != null ? suggested.getId() : null,
                    suggested != null ? suggested.getName() : null,
                    duplicate,
                    looksLikeTransfer(row.description()));
        }).toList();

        return new ImportPreviewDto(rows, skipped);
    }

    @Transactional
    public ImportResultDto confirmImport(User user, ImportConfirmRequest request) {
        if (request.rows().size() > MAX_ROWS) {
            throw new BadRequestException("Najviše " + MAX_ROWS + " transakcija po uvozu");
        }
        int created = 0;
        int learned = 0;
        for (ImportConfirmRequest.Row row : request.rows()) {
            if (row.transferAccountId() != null) {
                // Red je prebacivanje izmedju vlastitih racuna: rashod znaci "otislo NA drugi
                // racun", prihod znaci "doslo SA drugog racuna"
                boolean outgoing = row.type() == TransactionType.EXPENSE;
                transferService.createTransfer(user, new TransferRequest(
                        row.amount(), row.date(), row.description(),
                        outgoing ? request.accountId() : row.transferAccountId(),
                        outgoing ? row.transferAccountId() : request.accountId()));
                created++;
                continue;
            }
            transactionService.createTransaction(user, new TransactionRequest(
                    row.amount(), row.date(), row.type(), row.description(),
                    request.accountId(), row.categoryId(), null));
            created++;
            if (request.learnRules() && row.categoryId() != null) {
                if (learnRule(user, row.description(), row.categoryId())) {
                    learned++;
                }
            }
        }
        return new ImportResultDto(created, learned);
    }

    // "PRENOS NA RACUN..." u opisu obicno znaci prebacivanje izmedju vlastitih racuna
    static boolean looksLikeTransfer(String description) {
        String normalized = normalize(description);
        return normalized.contains("PRENOS") || normalized.contains("PRIJENOS")
                || normalized.contains("TRANSFER") || normalized.contains("PREBACIVANJE");
    }

    // ---------- kategorizacija ----------

    private Category suggestCategory(ParsedRow row, List<CategoryRule> userRules, List<Category> categories) {
        String normalized = normalize(row.description());
        if (normalized.isBlank()) {
            return null;
        }
        // Naucena pravila korisnika imaju prednost nad ugradjenim
        for (CategoryRule rule : userRules) {
            if (normalized.contains(rule.getPattern()) && rule.getCategory().getType() == row.type()) {
                return rule.getCategory();
            }
        }
        for (Map.Entry<String, List<String>> entry : BUILTIN_KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (normalized.contains(normalize(keyword))) {
                    Category match = categories.stream()
                            .filter(c -> c.getName().equalsIgnoreCase(entry.getKey())
                                    && c.getType() == row.type())
                            .findFirst().orElse(null);
                    if (match != null) {
                        return match;
                    }
                }
            }
        }
        return null;
    }

    private boolean learnRule(User user, String description, Long categoryId) {
        String pattern = merchantPattern(description);
        if (pattern.length() < 3) {
            return false;
        }
        Category category = categoryService.findOwned(user, categoryId);
        List<CategoryRule> existing = em
                .createNamedQuery(CategoryRule.GET_BY_USER_AND_PATTERN, CategoryRule.class)
                .setParameter("id", user.getId())
                .setParameter("pattern", pattern)
                .getResultList();
        if (!existing.isEmpty()) {
            existing.getFirst().setCategory(category);
            return false;
        }
        CategoryRule rule = new CategoryRule();
        rule.setPattern(pattern);
        rule.setCategory(category);
        rule.setUser(em.getReference(User.class, user.getId()));
        em.persist(rule);
        return true;
    }

    // "VOLI 7 PODGORICA 0234" -> "VOLI", "M TEL RACUN" -> "M TEL"
    static String merchantPattern(String description) {
        String normalized = normalize(description);
        String[] tokens = normalized.split(" ");
        if (tokens.length == 0 || tokens[0].isBlank()) {
            return "";
        }
        if (tokens[0].length() >= 4 || tokens.length == 1) {
            return tokens[0];
        }
        return (tokens[0] + " " + tokens[1]).trim();
    }

    // Veliko slovo, bez cifara i interpunkcije, bez dijakritika — stabilna osnova za poklapanje
    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String upper = text.toUpperCase(Locale.ROOT)
                .replace('Š', 'S').replace('Đ', 'D').replace('Č', 'C').replace('Ć', 'C').replace('Ž', 'Z');
        return upper.replaceAll("[^A-Z&: ]", " ").replaceAll("\\s+", " ").trim();
    }

    // ---------- duplikati ----------

    private Set<String> existingTransactionKeys(User user, Account account, List<ParsedRow> parsed) {
        LocalDate min = parsed.stream().map(ParsedRow::date).min(LocalDate::compareTo).orElseThrow();
        LocalDate max = parsed.stream().map(ParsedRow::date).max(LocalDate::compareTo).orElseThrow();
        Set<String> keys = new HashSet<>();

        List<Transaction> existing = em.createQuery(
                        "select t from Transaction t where t.account.id = :accountId"
                                + " and t.date >= :min and t.date <= :max", Transaction.class)
                .setParameter("accountId", account.getId())
                .setParameter("min", min)
                .setParameter("max", max)
                .getResultList();
        for (Transaction t : existing) {
            keys.add(transactionKey(t.getDate(), t.getAmount(), t.getType()));
        }

        // I vec zabiljezena prebacivanja su duplikati: odliv sa racuna izgleda kao
        // rashod u izvodu, priliv kao prihod
        List<Transfer> transfers = em.createQuery(
                        "select t from Transfer t where (t.fromAccount.id = :accountId or t.toAccount.id = :accountId)"
                                + " and t.date >= :min and t.date <= :max", Transfer.class)
                .setParameter("accountId", account.getId())
                .setParameter("min", min)
                .setParameter("max", max)
                .getResultList();
        for (Transfer t : transfers) {
            TransactionType type = t.getFromAccount().getId().equals(account.getId())
                    ? TransactionType.EXPENSE : TransactionType.INCOME;
            keys.add(transactionKey(t.getDate(), t.getAmount(), type));
        }
        return keys;
    }

    private String transactionKey(LocalDate date, BigDecimal amount, TransactionType type) {
        return date + "|" + amount.stripTrailingZeros().toPlainString() + "|" + type;
    }

    // ---------- CSV parser ----------

    List<ParsedRow> parseCsv(InputStream input, List<String> skipped) {
        List<String> lines = readLines(input);
        if (lines.isEmpty()) {
            return List.of();
        }
        char delimiter = detectDelimiter(lines.getFirst());

        String[] headerCells = splitLine(lines.getFirst(), delimiter);
        ColumnMap columns = mapColumns(headerCells);
        int startLine = columns != null ? 1 : 0;
        if (columns == null) {
            // Nema prepoznatljivog zaglavlja: pretpostavka datum;opis;iznos
            columns = new ColumnMap(0, 1, 2, -1, -1);
        }

        List<ParsedRow> rows = new ArrayList<>();
        for (int i = startLine; i < lines.size() && rows.size() < MAX_ROWS; i++) {
            String line = lines.get(i);
            if (line.isBlank()) {
                continue;
            }
            String[] cells = splitLine(line, delimiter);
            try {
                LocalDate date = parseDate(cell(cells, columns.date()));
                String description = cell(cells, columns.description());
                BigDecimal amount;
                TransactionType type;
                if (columns.amount() >= 0) {
                    amount = parseNumber(cell(cells, columns.amount()));
                    type = amount.signum() >= 0 ? TransactionType.INCOME : TransactionType.EXPENSE;
                    amount = amount.abs();
                } else {
                    BigDecimal credit = parseNumberOrZero(cell(cells, columns.credit()));
                    BigDecimal debit = parseNumberOrZero(cell(cells, columns.debit()));
                    if (credit.signum() > 0) {
                        amount = credit;
                        type = TransactionType.INCOME;
                    } else {
                        amount = debit.abs();
                        type = TransactionType.EXPENSE;
                    }
                }
                if (amount.signum() == 0) {
                    skipped.add("Red " + (i + 1) + ": iznos je nula");
                    continue;
                }
                rows.add(new ParsedRow(i + 1, date, description, amount, type));
            } catch (RuntimeException e) {
                skipped.add("Red " + (i + 1) + ": " + e.getMessage());
            }
        }
        return rows;
    }

    private record ColumnMap(int date, int description, int amount, int credit, int debit) {
    }

    private ColumnMap mapColumns(String[] header) {
        int date = -1, description = -1, amount = -1, credit = -1, debit = -1;
        for (int i = 0; i < header.length; i++) {
            String cell = normalize(header[i]);
            if (date < 0 && (cell.contains("DATUM") || cell.contains("DATE"))) date = i;
            else if (description < 0 && (cell.contains("OPIS") || cell.contains("SVRHA")
                    || cell.contains("NAZIV") || cell.contains("DESCRIPTION") || cell.contains("PRIMALAC"))) description = i;
            else if (amount < 0 && (cell.contains("IZNOS") || cell.contains("AMOUNT"))) amount = i;
            else if (credit < 0 && (cell.contains("UPLATA") || cell.contains("POTRAZUJE")
                    || cell.contains("PRILIV") || cell.contains("CREDIT"))) credit = i;
            else if (debit < 0 && (cell.contains("ISPLATA") || cell.contains("DUGUJE")
                    || cell.contains("ODLIV") || cell.contains("DEBIT"))) debit = i;
        }
        boolean hasAmount = amount >= 0 || (credit >= 0 && debit >= 0);
        if (date < 0 || description < 0 || !hasAmount) {
            return null;
        }
        return new ColumnMap(date, description, amount, credit, debit);
    }

    private List<String> readLines(InputStream input) {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (first) {
                    if (!line.isEmpty() && line.charAt(0) == (char) 0xFEFF) {
                        line = line.substring(1); // BOM na pocetku fajla
                    }
                    first = false;
                }
                lines.add(line);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Čitanje fajla nije uspjelo", e);
        }
        return lines;
    }

    private char detectDelimiter(String line) {
        long semicolons = line.chars().filter(c -> c == ';').count();
        long commas = line.chars().filter(c -> c == ',').count();
        long tabs = line.chars().filter(c -> c == '\t').count();
        if (tabs > semicolons && tabs > commas) return '\t';
        return semicolons >= commas ? ';' : ',';
    }

    // Postuje navodnike: "Kupovina, market";120 su dvije celije, ne tri
    private String[] splitLine(String line, char delimiter) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (char c : line.toCharArray()) {
            if (c == '"') {
                quoted = !quoted;
            } else if (c == delimiter && !quoted) {
                cells.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        cells.add(current.toString().trim());
        return cells.toArray(new String[0]);
    }

    private String cell(String[] cells, int index) {
        return index >= 0 && index < cells.length ? cells[index] : "";
    }

    private LocalDate parseDate(String value) {
        String trimmed = value.trim();
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(trimmed, format);
            } catch (DateTimeParseException ignored) {
                // probaj sljedeci format
            }
        }
        throw new BadRequestException("nepoznat format datuma \"" + value + "\"");
    }

    static BigDecimal parseNumber(String value) {
        String cleaned = value.replaceAll("[^0-9,.\\-]", "");
        if (cleaned.isBlank() || cleaned.equals("-")) {
            throw new BadRequestException("iznos nedostaje");
        }
        int lastComma = cleaned.lastIndexOf(',');
        int lastDot = cleaned.lastIndexOf('.');
        if (lastComma >= 0 && lastDot >= 0) {
            // decimalni separator je onaj koji je posljednji, drugi je hiljade
            if (lastComma > lastDot) {
                cleaned = cleaned.replace(".", "").replace(',', '.');
            } else {
                cleaned = cleaned.replace(",", "");
            }
        } else if (lastComma >= 0) {
            cleaned = cleaned.replace(',', '.');
        }
        return new BigDecimal(cleaned);
    }

    private BigDecimal parseNumberOrZero(String value) {
        try {
            return parseNumber(value);
        } catch (RuntimeException e) {
            return BigDecimal.ZERO;
        }
    }
}
