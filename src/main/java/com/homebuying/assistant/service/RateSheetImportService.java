package com.homebuying.assistant.service;

import com.homebuying.assistant.model.LoanOffer;
import com.homebuying.assistant.repository.LoanOfferRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.regex.*;

@Service
public class RateSheetImportService {

    private final LoanOfferRepository repo;

    public RateSheetImportService(LoanOfferRepository repo) {
        this.repo = repo;
    }

    /**
     * Parses rows like:
     * "30 Yr Fixed Up to $806,500 0.000 6.125% 6.232%"
     * points = 0.000, rate = 6.125
     */
    public int importRateSheet(String filename, byte[] pdfBytes) throws Exception {
        String text = extractText(pdfBytes);

        // matches 15/30 Yr Fixed rows:
        // group1 termYears (15/30)
        // group2 points (0.000 / 1.000 etc)
        // group3 rate (6.125)
        Pattern row = Pattern.compile(
                "(?i)\\b(15|30)\\s*Yr\\s*Fixed\\b.*?\\s(\\d+\\.\\d{3})\\s+(\\d+\\.\\d{3})%\\s+(\\d+\\.\\d{3})%",
                Pattern.MULTILINE
        );

        int saved = 0;
        Matcher m = row.matcher(text);

        while (m.find()) {
            int termYears = Integer.parseInt(m.group(1));
            double points = Double.parseDouble(m.group(2)); // discount points (percent of loan)
            double rate = Double.parseDouble(m.group(3));   // interest rate %

            LoanOffer o = new LoanOffer();
            o.setLenderName(filename);
            o.setTermYears(termYears);
            o.setRate(rate);

            // store points separately (so we can convert to $ later)
            o.setDiscountPoints(points);

            // optional if you have it; otherwise keep null
            // o.setFees(null);

            // optional credit score filtering; keep null for “any”
            // o.setMinScore(null); etc.

            repo.save(o);
            saved++;
        }

        return saved;
    }

    private String extractText(byte[] pdfBytes) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(doc);
        }
    }

}
