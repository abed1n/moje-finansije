package me.fit.service;

import me.fit.model.TransactionType;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Testira CKB PDF parser na sinteticki generisanom izvodu (isti raspored kolona kao CKB).
class CkbPdfParserTest {

    @Test
    void parsesTransactionsWithBalanceDiff() throws Exception {
        byte[] pdf = buildStatement();

        List<ImportService.ParsedRow> rows = CkbPdfParser.parse(pdf);

        assertEquals(3, rows.size(), "Očekivane 3 transakcije");

        // Prethodno stanje 1000,00 -> 950,00 = rashod 50
        assertEquals(TransactionType.EXPENSE, rows.get(0).type());
        assertEquals(0, new BigDecimal("50.00").compareTo(rows.get(0).amount()));
        assertEquals("TEST MARKET PODGORICA", rows.get(0).description());

        // 950,00 -> 1450,00 = prihod 500
        assertEquals(TransactionType.INCOME, rows.get(1).type());
        assertEquals(0, new BigDecimal("500.00").compareTo(rows.get(1).amount()));

        // 1450,00 -> 1437,50 = rashod 12,50 (iznos zalijepljen uz opis mora biti skinut)
        assertEquals(TransactionType.EXPENSE, rows.get(2).type());
        assertEquals(0, new BigDecimal("12.50").compareTo(rows.get(2).amount()));
        assertEquals("RESTORAN MNE", rows.get(2).description());
    }

    @Test
    void detectsPdfByMagicBytes() {
        assertEquals(true, CkbPdfParser.isPdf("%PDF-1.7\n...".getBytes()));
        assertEquals(false, CkbPdfParser.isPdf("Datum;Opis;Iznos".getBytes()));
    }

    // Generise minimalni izvod sa istim x-pozicijama kolona kao CKB PDF
    private byte[] buildStatement() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float top = 760;
                text(cs, font, 323, top, "(A) Prethodno stanje:");
                text(cs, font, 547, top, "1.000,00");
                // zaglavlje tabele
                text(cs, font, 59, top - 40, "Datum");
                text(cs, font, 99, top - 40, "Opis");
                text(cs, font, 415, top - 40, "Priliv");
                text(cs, font, 464, top - 40, "Odliv");
                text(cs, font, 504, top - 40, "Provizija");
                text(cs, font, 563, top - 40, "Stanje");
                // transakcije (iznos izvodimo iz stanja)
                text(cs, font, 53, top - 60, "01.06.2026");
                text(cs, font, 99, top - 60, "TEST MARKET PODGORICA");
                text(cs, font, 556, top - 60, "950,00");

                text(cs, font, 53, top - 80, "02.06.2026");
                text(cs, font, 99, top - 80, "PLATA FIRMA");
                text(cs, font, 415, top - 80, "500,00");
                text(cs, font, 556, top - 80, "1.450,00");

                // iznos zalijepljen uz opis (kao kod CKB POS redova)
                text(cs, font, 53, top - 100, "03.06.2026");
                text(cs, font, 99, top - 100, "RESTORAN MNE 12,50");
                text(cs, font, 556, top - 100, "1.437,50");

                text(cs, font, 53, top - 120, "Ukupno:");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private void text(PDPageContentStream cs, PDType1Font font, float x, float y, String s) throws Exception {
        cs.beginText();
        cs.setFont(font, 8);
        cs.newLineAtOffset(x, y);
        cs.showText(s);
        cs.endText();
    }
}
