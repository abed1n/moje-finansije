package me.fit.service;

import me.fit.model.TransactionType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Parser CKB "Univerzalni izvještaj" PDF-a (CKB nema CSV izvoz).
// Iznos i smjer (prihod/rashod) izvode se iz razlike tekuceg i prethodnog "Stanja" -
// otporno je na to da li PDFBox iznos izvuce kao zaseban fragment ili zalijepljen uz opis.
public final class CkbPdfParser {

    private static final Pattern DATE = Pattern.compile("^(\\d{2}\\.\\d{2}\\.\\d{4})");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    // Zalijepljeni iznos na kraju opisa, npr. "... MNE 75,00" -> ukloni " 75,00"
    private static final Pattern TRAILING_AMOUNT = Pattern.compile("\\s+-?[\\d.]*\\d,\\d{2}\\s*$");

    // Granice kolona (pikseli) iz CKB rasporeda: datum | opis | priliv/odliv/provizija | stanje
    private static final float OPIS_MIN = 90, OPIS_MAX = 405, STANJE_MIN = 535;

    private record Item(int page, float x, float y, String text) {
    }

    private record Line(int page, float y, List<Item> items) {
    }

    private CkbPdfParser() {
    }

    static boolean isPdf(byte[] bytes) {
        return bytes.length >= 5 && bytes[0] == '%' && bytes[1] == 'P'
                && bytes[2] == 'D' && bytes[3] == 'F' && bytes[4] == '-';
    }

    static List<ImportService.ParsedRow> parse(byte[] bytes) {
        List<Line> lines = groupLines(extract(bytes));

        List<ImportService.ParsedRow> rows = new ArrayList<>();
        BigDecimal prevStanje = null;
        boolean inTable = false;
        int counter = 0;

        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            String norm = lineText(line).toUpperCase(Locale.ROOT);

            // Prethodno stanje je pocetni saldo za balans-diff naredne tabele
            if (norm.contains("PRETHODNO STANJE")) {
                BigDecimal v = rightmostNumber(line);
                if (v != null) {
                    prevStanje = v;
                }
                continue;
            }
            // Zaglavlje tabele transakcija
            if (norm.contains("DATUM") && norm.contains("STANJE")
                    && (norm.contains("PRILIV") || norm.contains("ODLIV"))) {
                inTable = true;
                continue;
            }
            if (norm.startsWith("UKUPNO")) {
                inTable = false;
                continue;
            }
            if (!inTable) {
                continue;
            }

            // Transakcija: red ciji je najlijevi element datum
            Item leftmost = line.items().stream().min(Comparator.comparingDouble(Item::x)).orElse(null);
            if (leftmost == null) {
                continue;
            }
            Matcher dm = DATE.matcher(leftmost.text().trim());
            if (!dm.find()) {
                continue;
            }
            LocalDate date;
            try {
                date = LocalDate.parse(dm.group(1), DATE_FMT);
            } catch (RuntimeException e) {
                continue;
            }

            BigDecimal stanje = numberInRange(line, STANJE_MIN, Float.MAX_VALUE);
            // Prvi red opisa moze imati zalijepljen iznos (odliv) na kraju - skini ga prije nastavka
            StringBuilder desc = new StringBuilder(
                    TRAILING_AMOUNT.matcher(textInRange(line, OPIS_MIN, OPIS_MAX)).replaceAll(""));

            // Nastavak opisa (i eventualno stanje) iz narednih redova do sljedeceg datuma / Ukupno
            int j = i + 1;
            while (j < lines.size()) {
                Line cont = lines.get(j);
                Item cl = cont.items().stream().min(Comparator.comparingDouble(Item::x)).orElse(null);
                String cnorm = lineText(cont).toUpperCase(Locale.ROOT);
                if (cnorm.startsWith("UKUPNO") || (cl != null && DATE.matcher(cl.text().trim()).find())) {
                    break;
                }
                String more = textInRange(cont, OPIS_MIN, OPIS_MAX);
                if (!more.isBlank()) {
                    desc.append(' ').append(more);
                }
                if (stanje == null) {
                    stanje = numberInRange(cont, STANJE_MIN, Float.MAX_VALUE);
                }
                j++;
            }

            if (stanje == null || prevStanje == null) {
                continue;
            }
            BigDecimal delta = stanje.subtract(prevStanje);
            prevStanje = stanje;
            if (delta.signum() == 0) {
                continue;
            }
            TransactionType type = delta.signum() > 0 ? TransactionType.INCOME : TransactionType.EXPENSE;
            rows.add(new ImportService.ParsedRow(++counter, date,
                    cleanDescription(desc.toString()), delta.abs(), type));
        }
        return rows;
    }

    // ---------- izvlacenje teksta sa pozicijama ----------

    private static List<Item> extract(byte[] bytes) {
        List<Item> items = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeString(String text, List<TextPosition> positions) {
                    if (text != null && !text.isBlank() && !positions.isEmpty()) {
                        TextPosition f = positions.getFirst();
                        items.add(new Item(getCurrentPageNo(), f.getXDirAdj(), f.getYDirAdj(), text));
                    }
                }
            };
            stripper.setSortByPosition(true);
            stripper.getText(doc);
        } catch (IOException e) {
            throw new UncheckedIOException("Čitanje PDF-a nije uspjelo", e);
        }
        return items;
    }

    private static List<Line> groupLines(List<Item> items) {
        List<Item> sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparingInt(Item::page)
                .thenComparingDouble(Item::y).thenComparingDouble(Item::x));
        List<Line> lines = new ArrayList<>();
        List<Item> current = new ArrayList<>();
        int page = -1;
        float y = -1;
        for (Item it : sorted) {
            if (current.isEmpty()) {
                page = it.page();
                y = it.y();
                current.add(it);
            } else if (it.page() == page && Math.abs(it.y() - y) < 2.5f) {
                current.add(it);
            } else {
                lines.add(new Line(page, y, new ArrayList<>(current)));
                current.clear();
                page = it.page();
                y = it.y();
                current.add(it);
            }
        }
        if (!current.isEmpty()) {
            lines.add(new Line(page, y, current));
        }
        return lines;
    }

    // ---------- pomocne ----------

    private static String lineText(Line line) {
        return line.items().stream()
                .sorted(Comparator.comparingDouble(Item::x))
                .map(Item::text).reduce("", (a, b) -> (a + " " + b).trim());
    }

    private static String textInRange(Line line, float xMin, float xMax) {
        return line.items().stream()
                .filter(it -> it.x() >= xMin && it.x() < xMax)
                .sorted(Comparator.comparingDouble(Item::x))
                .map(it -> it.text().trim()).reduce("", (a, b) -> (a + " " + b).trim());
    }

    private static BigDecimal numberInRange(Line line, float xMin, float xMax) {
        return line.items().stream()
                .filter(it -> it.x() >= xMin && it.x() < xMax)
                .map(it -> tryNumber(it.text()))
                .filter(v -> v != null)
                .findFirst().orElse(null);
    }

    private static BigDecimal rightmostNumber(Line line) {
        return line.items().stream()
                .sorted(Comparator.comparingDouble(Item::x).reversed())
                .map(it -> tryNumber(it.text()))
                .filter(v -> v != null)
                .findFirst().orElse(null);
    }

    private static BigDecimal tryNumber(String text) {
        String t = text.trim();
        if (!t.matches(".*\\d,\\d{2}.*") && !t.matches(".*\\d\\.\\d{2}.*")) {
            return null;
        }
        try {
            return ImportService.parseNumber(t);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String cleanDescription(String raw) {
        String d = TRAILING_AMOUNT.matcher(raw.trim()).replaceAll("");
        // Ukloni samo pravi zamjenski znak (U+FFFD) ako se pojavi; validna slova (š, ć...) ostaju
        d = d.replace("�", "");
        return d.replaceAll("\\s+", " ").trim();
    }
}
