package com.homebuying.assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.homebuying.assistant.model.RagChunk;
import com.homebuying.assistant.model.RagDocument;
import com.homebuying.assistant.repository.RagChunkRepo;
import com.homebuying.assistant.repository.RagDocumentRepo;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.apache.pdfbox.Loader;

import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RagService {
    private final RagDocumentRepo docRepo;
    private final RagChunkRepo chunkRepo;
    private final EmbeddingService emb;
    private final ObjectMapper om = new ObjectMapper();

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

            String text = extractPdfText(f);           // your existing text extractor
            if (text == null) text = "";
            log.info("[RAG] {} → extracted {} chars", f.getName(), text.length());

            // If almost no text, try OCR fallback (stub for now)
            if (text.strip().length() < 50) {
                text = ocrExtractText(f);              // returns null if not wired yet
                log.info("[RAG] {} → OCR chars {}", f.getName(), (text == null ? 0 : text.length()));
            }
            if (text == null || text.strip().isEmpty()) {
                log.warn("[RAG] {} → no text, skipping", f.getName());
                continue;
            }

            List<String> chunks = splitText(text, maxChars, overlap);

            RagDocument doc = new RagDocument();
            doc.filename = f.getName();
            try (PDDocument pd = Loader.loadPDF(f)) {   // keep using Loader like your file
                doc.pages = pd.getNumberOfPages();
            }
            doc = docRepo.save(doc);

            for (String ch : chunks) {
                double[] vec = emb.embed(ch);
                RagChunk rc = new RagChunk();
                rc.document = doc;
                rc.text = ch;
                rc.embeddingJson = om.writeValueAsString(vec);
                chunkRepo.save(rc);
                added++;
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

        double[] qvec = emb.embed(qx);

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

}
