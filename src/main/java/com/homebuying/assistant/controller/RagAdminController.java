//package com.homebuying.assistant.controller;
//
//import com.homebuying.assistant.repository.RagChunkRepo;
//import com.homebuying.assistant.repository.RagDocumentRepo;
//import com.homebuying.assistant.service.RagService;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//import java.util.List;
//import java.util.Map;
//import java.util.Objects;
//
//@RestController
//@RequestMapping("/api/rag")
//public class RagAdminController {
//    private final RagService ragService;
//    private final RagChunkRepo chunkRepo;
//    private final RagDocumentRepo docRepo;
//
//    public RagAdminController(RagService ragService, RagChunkRepo chunkRepo, RagDocumentRepo docRepo) {
//        this.ragService = ragService;
//        this.chunkRepo = chunkRepo;
//        this.docRepo = docRepo;
//    }
//
//    @PostMapping("/reindex")
//    public ResponseEntity<?> reindex(@RequestParam(value="force", defaultValue="false") boolean force) throws Exception {
//        if (force) {
//            chunkRepo.deleteAll();
//            docRepo.deleteAll();
//        }
//        int n = ragService.indexAll();
//        return ResponseEntity.ok(Map.of("indexedChunks", n, "forced", force));
//    }
//
//    @PostMapping("/api/rag/hits")
//    public ResponseEntity<?> hits(@RequestBody Map<String,String> body) throws Exception {
//        String q = Objects.toString(body.get("q"), "");
//        List<RagService.RagHit> top = ragService.retrieveTop(q, 8);
//        var out = top.stream().map(h -> Map.of(
//                "score", String.format("%.3f", h.score),
//                "file", h.chunk.document.filename,
//                "snippet", h.chunk.text.length()>240 ? h.chunk.text.substring(0,240)+"…" : h.chunk.text
//        ));
//        return ResponseEntity.ok(out);
//    }
//
//}
package com.homebuying.assistant.controller;

import com.homebuying.assistant.repository.RagChunkRepo;
import com.homebuying.assistant.repository.RagDocumentRepo;
import com.homebuying.assistant.service.RagService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/rag")
public class RagAdminController {
    private final RagService ragService;
    private final RagChunkRepo chunkRepo;
    private final RagDocumentRepo docRepo;

    public RagAdminController(RagService ragService, RagChunkRepo chunkRepo, RagDocumentRepo docRepo) {
        this.ragService = ragService;
        this.chunkRepo = chunkRepo;
        this.docRepo = docRepo;
    }

    @PostMapping("/reindex")
    public ResponseEntity<?> reindex(@RequestParam(value="force", defaultValue="false") boolean force) throws Exception {
        if (force) {
            chunkRepo.deleteAll();
            docRepo.deleteAll();
        }
        int n = ragService.indexAll();
        return ResponseEntity.ok(Map.of("indexedChunks", n, "forced", force));
    }

    // ✅ FIXED: this is now /api/rag/hits
    @PostMapping("/hits")
    public ResponseEntity<?> hits(@RequestBody Map<String,String> body) throws Exception {
        String q = Objects.toString(body.get("q"), "");
        List<RagService.RagHit> top = ragService.retrieveTop(q, 8);
        var out = top.stream().map(h -> Map.of(
                "score", String.format("%.3f", h.score),
                "file",  h.chunk.document.filename,
                "snippet", h.chunk.text.length() > 240 ? h.chunk.text.substring(0,240) + "…" : h.chunk.text
        )).toList();
        return ResponseEntity.ok(out);
    }
}
