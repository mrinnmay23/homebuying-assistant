package com.homebuying.assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.homebuying.assistant.model.PropertyFact;
import com.homebuying.assistant.model.RagChunk;
import com.homebuying.assistant.model.RagDocument;
import com.homebuying.assistant.repository.PropertyFactRepo;
import com.homebuying.assistant.repository.RagChunkRepo;
import com.homebuying.assistant.repository.RagDocumentRepo;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.apache.pdfbox.Loader;

import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class RagService {
    private final RagDocumentRepo docRepo;
    private final RagChunkRepo chunkRepo;
    private final EmbeddingService emb;
    private final ObjectMapper om = new ObjectMapper();

    @Autowired
    private PropertyFactRepo propertyFactRepo;

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    @Value("${rag.corpus.path:./corpus}")
    private String corpusPath;

    @Value("${rag.chunk.maxChars:1600}")
    private int maxChars;

    @Value("${rag.chunk.overlap:200}")
    private int overlap;

    @Value("${rag.topK:12}")
    private int topK;

    @Value("${rag.minScore:0.55}")
    private double minScore;


    public RagService(RagDocumentRepo d, RagChunkRepo c, EmbeddingService e){
        this.docRepo=d; this.chunkRepo=c; this.emb=e;
    }

    // --- INDEX ALL PDFs IN corpus/ ---
//    @Transactional
//    public int indexAll() throws Exception {
//        File dir = Paths.get(corpusPath).toFile();
//        if (!dir.exists()) throw new IllegalStateException("Corpus folder not found: "+dir.getAbsolutePath());
//
//        int added = 0;
//        for (File f : Objects.requireNonNull(dir.listFiles((d, name) -> name.toLowerCase().endsWith(".pdf")))) {
//            if (docRepo.findByFilename(f.getName()).isPresent()) continue;
//
//            String text = extractPdfText(f);
//            List<String> chunks = splitText(text, maxChars, overlap);
//
//            RagDocument doc = new RagDocument();
//            doc.filename = f.getName();
//            // (A) when counting pages
//            try (PDDocument pd = Loader.loadPDF(f)) {
//                doc.pages = pd.getNumberOfPages();
//            }
//
//            doc = docRepo.save(doc);
//
//            for (String ch : chunks) {
//                double[] vec = emb.embed(ch);
//                RagChunk rc = new RagChunk();
//                rc.document = doc;
//                rc.text = ch;
//                rc.embeddingJson = om.writeValueAsString(vec);
//                chunkRepo.save(rc);
//                added++;
//            }
//        }
//        return added;
//    }
    @Transactional
    public int indexAll() throws Exception {
        // --- NEW: recursive scan + logging + OCR hook ---

        // use java.nio Paths instead of File.listFiles()
        Path root = Paths.get(corpusPath);
        if (!Files.exists(root)) {
            log.warn("[RAG] corpus path does not exist: {}", root.toAbsolutePath());
            return 0;
        }

        List<File> pdfs = new ArrayList<>();
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            paths.filter(p -> p.toString().toLowerCase().endsWith(".pdf"))
                    .forEach(p -> pdfs.add(p.toFile()));
        }
        log.info("[RAG] Found {} PDFs under {}", pdfs.size(), root.toAbsolutePath());

        int added = 0;
        for (File f : pdfs) {
            if (docRepo.findByFilename(f.getName()).isPresent()) {
                log.info("[RAG] Skip already indexed {}", f.getName());
                continue;
            }

            log.info("[RAG] Indexing {}", f.getName());

//            String text = extractPdfText(f);           // your existing text extractor
//            if (text == null) text = "";
//            log.info("[RAG] {} → extracted {} chars", f.getName(), text.length());
//
//            // If almost no text, try OCR fallback (stub for now)
//            if (text.strip().length() < 50) {
//                text = ocrExtractText(f);              // returns null if not wired yet
//                log.info("[RAG] {} → OCR chars {}", f.getName(), (text == null ? 0 : text.length()));
//            }
//            if (text == null || text.strip().isEmpty()) {
//                log.warn("[RAG] {} → no text, skipping", f.getName());
//                continue;
//            }
//
//            List<String> chunks = splitText(text, maxChars, overlap);

            List<PageText> pages = extractPerPage(f);
            if (pages.isEmpty()) {
                log.warn("[RAG] {} → no text, skipping", f.getName());
                continue;
            }

            List<String> chunks = new ArrayList<>();
            List<Integer> chunkPage = new ArrayList<>();

            for (PageText ptxt : pages) {
                for (String ch : splitText(ptxt.text, maxChars, overlap)) {
                    chunks.add(ch);
                    chunkPage.add(ptxt.page); // remember the page
                }
            }


            RagDocument doc = new RagDocument();
            doc.filename = f.getName();
            try (PDDocument pd = Loader.loadPDF(f)) {   // keep using Loader like your file
                doc.pages = pd.getNumberOfPages();
            }
            doc = docRepo.save(doc);

//            for (String ch : chunks) {
//                double[] vec = emb.embed(ch);
//                RagChunk rc = new RagChunk();
//                rc.document = doc;
//                rc.text = ch;
//                rc.embeddingJson = om.writeValueAsString(vec);
//                chunkRepo.save(rc);
//                added++;
//            }

            for (int i = 0; i < chunks.size(); i++) {
                String ch = chunks.get(i);
//                double[] vec = emb.embed(ch);
//                RagChunk rc = new RagChunk();
//                rc.document = doc;
//                rc.text = ch;
//                rc.pageStart = chunkPage.get(i);
//                rc.pageEnd = chunkPage.get(i);
//                rc.embeddingJson = om.writeValueAsString(vec);
//                chunkRepo.save(rc);
//                added++;

                double[] vec = emb.embed(ch);
                if (vec == null) {
                    log.warn("[RAG] embedding failed; skipping chunk ({} p{})", doc.filename, chunkPage.get(i));
                    continue;
                }

                RagChunk rc = new RagChunk();
                rc.document = doc;
                rc.text = ch;
                rc.pageStart = chunkPage.get(i);
                rc.pageEnd = chunkPage.get(i);
                rc.embeddingJson = om.writeValueAsString(vec);
                chunkRepo.save(rc);
                added++;


                // ⬇️ ADD THIS LINE (persist structured facts if we can parse any)
                try {
                    tryExtractFact(doc, ch, rc.pageStart).ifPresent(propertyFactRepo::save);
                } catch (Exception ex) {
                    log.warn("[RAG] fact extract failed for {} p{}: {}", doc.filename, rc.pageStart, ex.toString());
                }
            }

        }

        log.info("[RAG] Indexed chunks: {}", added);
        return added;
    }


    // --- ASK: retrieve top chunks by cosine & build prompt ---
//    public List<RagHit> retrieveTop(String question, int k) throws Exception {
//        double[] qvec = emb.embed(question);
//        List<RagChunk> all = chunkRepo.findAll();
//
//        List<RagHit> scored = new ArrayList<>(all.size());
//        for (RagChunk c : all) {
//            double[] v = om.readValue(c.embeddingJson, double[].class);
//            double score = EmbeddingService.cosine(qvec, v);
//            scored.add(new RagHit(c, score));
//        }
//        return scored.stream()
//                .sorted(Comparator.comparingDouble((RagHit h)->h.score).reversed())
//                .limit(k)
//                .collect(Collectors.toList());
//    }

//    public List<RagHit> retrieveTop(String question, int k) throws Exception {
//        // Use caller’s k if >0, otherwise the configured default
//        int K = (k > 0 ? k : topK);
//
//        // Light query expansion to improve recall on common finance terms
//        String qx = expandQuery(question);
//        double[] qvec = emb.embed(qx);
//
//        List<RagChunk> all = chunkRepo.findAll();
//        List<RagHit> scored = new ArrayList<>(all.size());
//
//        for (RagChunk c : all) {
//            double[] v = om.readValue(c.embeddingJson, double[].class);
//            double score = EmbeddingService.cosine(qvec, v);
//            scored.add(new RagHit(c, score));
//        }
//
//        return scored.stream()
//                .sorted(Comparator.comparingDouble((RagHit h) -> h.score).reversed())
//                .limit(K)
//                .collect(Collectors.toList());
//    }

    public List<RagHit> retrieveTop(String question, int k) throws Exception {
        int K = (k > 0 ? k : topK);

        // Expand only for finance-ish questions; keep brochure/property questions clean
        boolean financeQ = question.matches("(?i)\\b(APR|TIP|interest|rate|loan|mortgage|closing|escrow|origination|points|cash\\s*to\\s*close|LE|CD|lock|LTV|FICO)\\b");
        String qx = financeQ ? expandQuery(question) : question;

    //    double[] qvec = emb.embed(qx);


        double[] qvec = emb.embed(qx, "RETRIEVAL_QUERY");

//        double[] qvec = emb.embed(qx); // OR emb.embed(qx, "RETRIEVAL_QUERY") if you add that overload
//        if (qvec == null) {
//            log.error("[RAG] Query embedding is null; returning no hits. q='{}'", qx);
//            return List.of();
//        }



        List<RagChunk> all = chunkRepo.findAll();
        List<RagHit> scored = new ArrayList<>(all.size());

        for (RagChunk c : all) {
            double[] v = om.readValue(c.embeddingJson, double[].class);
            double score = EmbeddingService.cosine(qvec, v);
            scored.add(new RagHit(c, score));
        }

        return scored.stream()
                .sorted(Comparator.comparingDouble((RagHit h) -> h.score).reversed())
                .limit(K)
                .collect(Collectors.toList());
    }



    // com.homebuying.assistant.service.RagService
    public File saveToCorpus(String originalName, byte[] bytes) throws Exception {
        Path dir = Paths.get(corpusPath);
        Files.createDirectories(dir);
        String safe = originalName.replaceAll("[^A-Za-z0-9._-]", "_");
        Path out = dir.resolve(safe);
        Files.write(out, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return out.toFile();
    }

    public int indexNewOnly() throws Exception {
        Path root = Paths.get(corpusPath);
        if (!Files.exists(root)) return 0;
        List<File> pdfs = new ArrayList<>();
        try (var paths = Files.walk(root)) {
            paths.filter(p -> p.toString().toLowerCase().endsWith(".pdf"))
                    .forEach(p -> pdfs.add(p.toFile()));
        }
        int added = 0;
        for (File f : pdfs) {
            if (docRepo.findByFilename(f.getName()).isPresent()) continue;
            added += indexSingleFile(f);
        }
        return added;
    }

    // factor out single-file indexing using the same per-page approach
    private int indexSingleFile(File f) throws Exception {
        List<PageText> pages = extractPerPage(f);
        if (pages.isEmpty()) return 0;

        RagDocument doc = new RagDocument();
        doc.filename = f.getName();
        try (PDDocument pd = Loader.loadPDF(f)) { doc.pages = pd.getNumberOfPages(); }
        doc = docRepo.save(doc);

        int added = 0;
        for (PageText p : pages) {
            for (String ch : splitText(p.text, maxChars, overlap)) {
//                double[] vec = emb.embed(ch);
//                RagChunk rc = new RagChunk();
//                rc.document = doc;
//                rc.text = ch;
//                rc.pageStart = p.page;
//                rc.pageEnd = p.page;
//                rc.embeddingJson = om.writeValueAsString(vec);
//                chunkRepo.save(rc);
//                added++;

                double[] vec = emb.embed(ch);
                if (vec == null) {
                    log.warn("[RAG] embedding failed; skipping chunk ({} p{})", doc.filename, p.page);
                    continue;
                }

                RagChunk rc = new RagChunk();
                rc.document = doc;
                rc.text = ch;
                rc.pageStart = p.page;
                rc.pageEnd = p.page;
                rc.embeddingJson = om.writeValueAsString(vec);
                chunkRepo.save(rc);
                added++;



                try {
                    tryExtractFact(doc, ch, rc.pageStart).ifPresent(propertyFactRepo::save);
                } catch (Exception ex) {
                    log.warn("[RAG] fact extract failed for {} p{}: {}", doc.filename, rc.pageStart, ex.toString());
                }
            }
        }
        return added;
    }



    // very naive extractors; replace with LLM later if you like
// very naive extractors; replace with LLM later if you like
    private Optional<PropertyFact> tryExtractFact(RagDocument doc, String text, int page){
        var pf = new PropertyFact();
        pf.document = doc;
        pf.page = page;

        // bedrooms: "3 bedroom" / "3BR" / "3-bed"
        // bedrooms: "3 bedroom", "3BR", "3-bed", "3+ bedrooms"
        var mBed = Pattern.compile("(\\d+)[\\s+]*(?:bed(?:room)?s?|br)\\b", Pattern.CASE_INSENSITIVE)
                .matcher(text);
        if (mBed.find()) pf.bedrooms = parseIntSafe(mBed.group(1));

// bathrooms: "2 bath", "2.5 baths", "2BA"
        var mBath = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(?:bath(?:room)?s?|ba)\\b", Pattern.CASE_INSENSITIVE)
                .matcher(text);
        if (mBath.find()) pf.bathrooms = parseBathsToInt(mBath.group(1));

// price: "£365,000" / "$450k" / "365 000" / "1.2m"
        var mPrice = Pattern.compile(
                "\\$?\\s*(?:(?<plain>(?<!\\d)[1-9]\\d{4,6}(?!\\d))|(?<k>\\d+(?:\\.\\d+)?)\\s*[kK])"
        ).matcher(text);

        if (mPrice.find()) {
            if (mPrice.group("plain") != null) {
                try {
                    pf.price = Integer.parseInt(mPrice.group("plain").replaceAll(",", ""));
                } catch (NumberFormatException ignored) {}
            } else if (mPrice.group("k") != null) {
                double k = Double.parseDouble(mPrice.group("k"));
                pf.price = (int)Math.round(k * 1000);
            }
        }

// final sanity: drop clearly bogus prices
        if (pf.price != null && pf.price < 10000) {
            pf.price = null;
        }


        // -------- NEW city extraction (generic) --------
        // 1) Try to infer city from a UK/German style postcode line (works well on brochures)
        Pattern ukPostcode = Pattern.compile("\\b[A-Z]{1,2}\\d[A-Z0-9]?\\s*\\d[A-Z]{2}\\b");
        var mPC = ukPostcode.matcher(text);
        if (pf.city == null && mPC.find()) {
            String before = text.substring(Math.max(0, mPC.start() - 60), mPC.start());
            var mCityFromPC = Pattern.compile("([A-Z][A-Za-z\\-]+(?:\\s+[A-Z][A-Za-z\\-]+){0,2})\\s*$").matcher(before);
            if (mCityFromPC.find()) pf.city = mCityFromPC.group(1).trim();
        }

        // 2) Generic “in/at/located in <City Name>” pattern
        if (pf.city == null) {
            var mCity2 = Pattern.compile(
                            "\\b(?:in|at|located in)\\s+([A-Z][A-Za-z\\-]+(?:\\s+[A-Z][A-Za-z\\-]+){0,2})")
                    .matcher(text);
            if (mCity2.find()) pf.city = mCity2.group(1).trim();
        }
        // -------- end NEW city extraction --------

        // amenities: broaden a bit (matches seen in brochure)
        var am = new ArrayList<String>();
        for (String kw : List.of("parking","garage","balcony","garden","concierge","cycle storage","gym","terrace")) {
            if (text.toLowerCase().contains(kw)) am.add(kw);
        }
        pf.amenities = String.join(", ", am);

        // Only persist if at least two useful fields found
        int score=0; if (pf.bedrooms!=null) score++; if (pf.price!=null) score++; if (pf.city!=null) score++;
        if (score >= 2) return Optional.of(pf);
        return Optional.empty();
    }




//    public List<RagHit> retrieveTop(String question, int k) throws Exception {
//        int K = (k > 0 ? k : topK);
//
//        String qx = expandQuery(question);
//        double[] qvec = emb.embed(qx);
//
//        List<RagChunk> all = chunkRepo.findAll();
//        List<RagHit> scored = new ArrayList<>(all.size());
//        for (RagChunk c : all) {
//            double[] v = om.readValue(c.embeddingJson, double[].class);
//            double score = EmbeddingService.cosine(qvec, v);
//            scored.add(new RagHit(c, score));
//        }
//
//        // ✅ filter by configured minScore BEFORE limiting
//        List<RagHit> filtered = scored.stream()
//                .sorted(Comparator.comparingDouble((RagHit h) -> h.score).reversed())
//                .filter(h -> h.score >= minScore)
//                .limit(K)
//                .collect(Collectors.toList());
//
//        if (filtered.isEmpty()) {
//            log.info("[RAG] No hits >= minScore {} for query: {}", String.format("%.2f", minScore), question);
//        }
//        return filtered;
//    }


    // utility
    // (B) when extracting text
//    private static String extractPdfText(File f) throws Exception {
//        try (PDDocument pd = Loader.loadPDF(f)) {
//            PDFTextStripper stripper = new PDFTextStripper();
//            stripper.setSortByPosition(true);
//            stripper.setLineSeparator("\n");
//            return stripper.getText(pd);
//        }
//    }

    private static String extractPdfText(File f) throws Exception {
        try (PDDocument pd = Loader.loadPDF(f)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);        // <-- preserve human reading order
            return stripper.getText(pd);
        }
    }


    private static class PageText {
        final int page; final String text;
        PageText(int page, String text){ this.page = page; this.text = text; }
    }

    private static List<PageText> extractPerPage(File f) throws Exception {
        List<PageText> out = new ArrayList<>();
        try (PDDocument pd = Loader.loadPDF(f)) {
            PDFTextStripper s = new PDFTextStripper();
            s.setSortByPosition(true);
            for (int p = 1; p <= pd.getNumberOfPages(); p++) {
                s.setStartPage(p);
                s.setEndPage(p);
                String t = s.getText(pd);
                if (t != null && !t.isBlank()) out.add(new PageText(p, t));
            }
        }
        return out;
    }


    private static List<String> splitText(String s, int max, int overlap) {
        s = s.replaceAll("\\s+"," ").trim();
        List<String> chunks = new ArrayList<>();
        int i=0;
        while (i < s.length()) {
            int end = Math.min(i+max, s.length());
            chunks.add(s.substring(i, end));
            if (end==s.length()) break;
            i = end - overlap;
            if (i<0) i=0;
        }
        return chunks;
    }

    /** Light query expansion so synonyms match more chunks */
    private String expandQuery(String q) {
        String extra = """
    Synonyms:
    LE=Loan Estimate; CD=Closing Disclosure; APR=annual percentage rate; TIP=total interest percentage;
    P&I=principal and interest; cash to close=estimated cash to close; MI=mortgage insurance;
    escrow=estimated taxes insurance assessments; origination charges=points application fee processing fee underwriting;
    projected payments=principal interest mortgage insurance escrow; rate=interest rate;
    pages=page; section A=origination charges; section B=services you cannot shop for; section C=services you can shop for;
    """;
        return q + "\n\n" + extra;
    }



    public static class RagHit {
        public final RagChunk chunk;
        public final double score;
        public RagHit(RagChunk c, double s){ this.chunk=c; this.score=s; }
    }

    /**
     * OCR fallback for image-only PDFs.
     * TODO: Replace with real OCR (Document AI / Gemini Vision) and return plain text.
     */
    private String ocrExtractText(File f) {
        log.warn("[RAG] OCR needed for {} but OCR is not configured yet.", f.getName());
        return null; // keeps current behavior (skip if no text)
    }



    // ---------- helpers: safe numeric parsing ----------
    // --- helpers ------------------------------------------------------------

    /** strip everything except digits, then parse; returns null if empty/bad */
    private static Integer parseIntSafe(String s) {
        if (s == null) return null;
        s = s.replaceAll("[^0-9]", "");   // removes spaces, '+' , commas, etc.
        if (s.isEmpty()) return null;
        try { return Integer.parseInt(s); } catch (Exception e) { return null; }
    }

    /** parse "2.5" → 3, "1.0" → 1, "2" → 2 (round halves up) */
    private static Integer parseBathsToInt(String s) {
        if (s == null) return null;
        s = s.trim();
        try {
            double d = Double.parseDouble(s.replace(',', '.'));
            return (int)Math.round(d);
        } catch (Exception e) {
            return parseIntSafe(s);
        }
    }

    /** parse prices like "£365,000", "365 000", "$450k", "1.2m" → integer GBP/USD/EUR */
    private static Integer parseMoneyToInt(String s) {
        if (s == null) return null;
        // normalise non-breaking/thin spaces
        s = s.replace('\u00A0',' ').replace('\u2009',' ').trim();
        // capture main number and optional scale k/m
        var m = java.util.regex.Pattern
                .compile("(?i)([0-9]{1,3}(?:[ ,.]\\d{3})+|\\d+(?:[.,]\\d+)?)\\s*([km])?")
                .matcher(s);
        if (!m.find()) return null;

        String num = m.group(1).replaceAll("[^0-9.]", "");
        double val = 0;
        try { val = Double.parseDouble(num); } catch (Exception ignore) { return null; }

        String scale = m.group(2);
        if (scale != null) {
            if (scale.equalsIgnoreCase("k")) val *= 1_000d;
            else if (scale.equalsIgnoreCase("m")) val *= 1_000_000d;
        }
        long l = Math.round(val);
        if (l > Integer.MAX_VALUE) l = Integer.MAX_VALUE;
        return (int) l;
    }




}
