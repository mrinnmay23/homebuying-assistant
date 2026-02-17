package com.homebuying.assistant.controller;

import com.homebuying.assistant.service.RateSheetImportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/rates")
public class RateSheetController {

    private final RateSheetImportService importer;

    public RateSheetController(RateSheetImportService importer) {
        this.importer = importer;
    }

    @PostMapping("/import")
    public ResponseEntity<?> importRateSheet(@RequestPart("file") MultipartFile file) throws Exception {
        if (file.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "empty file"));
        int saved = importer.importRateSheet(file.getOriginalFilename(), file.getBytes());
        return ResponseEntity.ok(Map.of("file", file.getOriginalFilename(), "offersSaved", saved));
    }
}
