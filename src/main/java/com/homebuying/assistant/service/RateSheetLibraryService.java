package com.homebuying.assistant.service;

import com.homebuying.assistant.model.RateSheetOffer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class RateSheetLibraryService {

    @Value("${fx.gbpToUsd:1.27}")
    private double gbpToUsd;

    @Value("${fx.eurToUsd:1.08}")
    private double eurToUsd;

    private static final Logger log = LoggerFactory.getLogger(RateSheetLibraryService.class);

    @Value("${rag.corpus.path:./corpus}")
    private String corpusPath;

    // simple in-memory cache so we don’t re-parse PDFs every single question
    private volatile long cacheBuiltAt = 0L;
    private volatile List<RateSheetOffer> cachedOffers = List.of();

    public List<RateSheetOffer> getRateSheetOffers() {
        try {
            Path root = Paths.get(corpusPath);
            if (!Files.exists(root)) {
                log.warn("[RateSheet] corpus path not found: {}", root.toAbsolutePath());
                return List.of();
            }

            // rebuild cache if any Rate sheet file changed
            long newest = newestRateSheetMtime(root);
            if (!cachedOffers.isEmpty() && newest <= cacheBuiltAt) return cachedOffers;

            List<File> rateSheets = findRateSheetPdfs(root);
            log.info("[RateSheet] Found {} rate-sheet PDFs", rateSheets.size());

            List<RateSheetOffer> all = new ArrayList<>();
            for (File f : rateSheets) {
                all.addAll(extractOffersFromPdf(f));
            }

            cachedOffers = List.copyOf(all);
            cacheBuiltAt = newest;
            log.info("[RateSheet] Parsed offers: {}", cachedOffers.size());

            return cachedOffers;

        } catch (Exception e) {
            log.warn("[RateSheet] failed reading offers: {}", e.toString());
            return List.of();
        }
    }

    private List<File> findRateSheetPdfs(Path root) throws Exception {
        List<File> pdfs = new ArrayList<>();
        try (Stream<Path> s = Files.walk(root)) {
            s.filter(p -> p.toString().toLowerCase().endsWith(".pdf"))
                    .filter(p -> p.getFileName().toString().toLowerCase().contains("rate sheet"))
                    .forEach(p -> pdfs.add(p.toFile()));
        }
        return pdfs;
    }

    private long newestRateSheetMtime(Path root) throws Exception {
        long newest = 0L;
        try (Stream<Path> s = Files.walk(root)) {
            Iterator<Path> it = s.iterator();
            while (it.hasNext()) {
                Path p = it.next();
                String name = p.getFileName().toString().toLowerCase();
                if (!name.endsWith(".pdf")) continue;
                if (!name.contains("rate sheet")) continue;
                try {
                    newest = Math.max(newest, Files.getLastModifiedTime(p).toMillis());
                } catch (Exception ignore) {}
            }
        }
        return newest;
    }

    // ---------- parsing (best-effort; tables can be messy in PDF text) ----------

    private List<RateSheetOffer> extractOffersFromPdf(File f) throws Exception {
        List<RateSheetOffer> out = new ArrayList<>();
        try (PDDocument pd = Loader.loadPDF(f)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            int pages = pd.getNumberOfPages();
            for (int page = 1; page <= pages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(pd);
                if (text == null || text.isBlank()) continue;

                out.addAll(extractOffersFromText(text, f.getName(), page));
            }
        }
        return out;
    }

    private List<RateSheetOffer> extractOffersFromText(String text, String filename, int page) {
        List<RateSheetOffer> out = new ArrayList<>();

        // break into lines and parse line-by-line (works better than parsing whole page)
        String[] lines = text.split("\\R");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;

            // Skip obvious non-data lines
            String low = line.toLowerCase();
            if (low.contains("rate sheet") || low.contains("lock") || low.contains("disclaimer")) continue;

            // Find a RATE on the line (e.g., 4.375% or 4.375)
            Double rate = findRatePercent(line);
            if (rate == null) continue;

            // Try to find points on same line (typical tables: rate | points)
            Double points = findLikelyPoints(line, rate);

            // Try to find a $ fee on same line
            Double feeDollars = findMoneyDollars(line);

            // Heuristic: only accept reasonable mortgage rates
            if (rate < 0.5 || rate > 20.0) continue;

            out.add(new RateSheetOffer(
                    filename,              // lender label = filename for now
                    filename,
                    page,
                    rate,
                    points,
                    feeDollars
            ));
        }

        return out;
    }

    private Double findRatePercent(String line) {
        // matches 3.875, 4.25, 6.000 with optional %
        Pattern p = Pattern.compile("(?<!\\d)(\\d{1,2}\\.\\d{2,3}|\\d{1,2}\\.\\d)(?=\\s*%?)(?!\\d)");
        Matcher m = p.matcher(line);
        if (!m.find()) return null;
        try {
            return Double.parseDouble(m.group(1));
        } catch (Exception e) {
            return null;
        }
    }

    private Double findLikelyPoints(String line, Double rate) {
        // points often look like: -0.125, 0.000, 0.875, 1.250
        // We grab all decimals on the line and pick one that looks like points (small magnitude),
        // not equal to rate.
        Pattern p = Pattern.compile("(?<!\\d)(-?\\d\\.\\d{3}|-?\\d\\.\\d{2})(?!\\d)");
        Matcher m = p.matcher(line);

        List<Double> nums = new ArrayList<>();
        while (m.find()) {
            try { nums.add(Double.parseDouble(m.group(1))); } catch (Exception ignore) {}
        }

        // remove the rate itself (or close to it)
        nums.removeIf(x -> Math.abs(x - rate) < 0.001);

        // points usually between -5 and +5
        for (Double x : nums) {
            if (x >= -5.0 && x <= 5.0) return x;
        }
        return null;
    }

//    private Double findMoneyDollars(String line) {
//        // matches $1,234 or 1234 (after $)
//        Pattern p = Pattern.compile("\\$\\s*([0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)");
//        Matcher m = p.matcher(line);
//        if (!m.find()) return null;
//        try {
//            return Double.parseDouble(m.group(1).replace(",", ""));
//        } catch (Exception e) {
//            return null;
//        }
//    }

//    private Double findMoneyDollars(String line) {
//        // matches $1,234 or €1,234 or 1234 after symbol
//        Pattern p = Pattern.compile("[\\$€]\\s*([0-9]{1,3}(?:[.,][0-9]{3})+|[0-9]+)");
//
//        Matcher m = p.matcher(line);
//        if (!m.find()) return null;
//        try {
//            return Double.parseDouble(m.group(1).replace(",", "").replace(".", ""));
//
//        } catch (Exception e) {
//            return null;
//        }
//    }




    private Double findMoneyDollars(String line) {
        // Matches $8,791  |  €8.791,50  |  $8791.50  |  €8791
        Pattern p = Pattern.compile("[\\$€]\\s*([0-9]+(?:[.,][0-9]{3})*(?:[.,][0-9]{1,2})?)");
        Matcher m = p.matcher(line);
        if (!m.find()) return null;

        return parseFlexibleNumber(m.group(1));
    }


    private Double parseFlexibleNumber(String s) {
        if (s == null) return null;
        String v = s.trim().replace(" ", "");

        boolean hasDot = v.contains(".");
        boolean hasComma = v.contains(",");

        try {
            if (hasDot && hasComma) {
                int lastDot = v.lastIndexOf('.');
                int lastComma = v.lastIndexOf(',');

                if (lastDot > lastComma) {
                    // US: 1,234.56
                    v = v.replace(",", "");
                } else {
                    // EU: 1.234,56
                    v = v.replace(".", "");
                    v = v.replace(",", ".");
                }
                return Double.parseDouble(v);
            }

            if (hasComma) {
                if (v.matches("^\\d{1,3}(,\\d{3})+$")) {
                    v = v.replace(",", "");
                    return Double.parseDouble(v);
                }
                if (v.matches("^\\d+,\\d{1,2}$")) {
                    v = v.replace(",", ".");
                    return Double.parseDouble(v);
                }
                v = v.replace(",", "");
                return Double.parseDouble(v);
            }

            if (hasDot) {
                if (v.matches("^\\d{1,3}(\\.\\d{3})+$")) {
                    v = v.replace(".", "");
                    return Double.parseDouble(v);
                }
                return Double.parseDouble(v);
            }

            return Double.parseDouble(v);

        } catch (Exception e) {
            return null;
        }
    }




}
