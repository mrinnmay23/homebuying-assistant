package com.homebuying.assistant.controller;

import com.homebuying.assistant.service.OcrVisionService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/ocr")
public class OcrController {

    private final OcrVisionService ocrService;

    public OcrController(OcrVisionService ocrService) {
        this.ocrService = ocrService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) {
        try {
            Map<String, Object> res = ocrService.ocrDocumentText(file);
            return ResponseEntity.ok(res);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }



    @PostMapping(value = "/summary.pdf", consumes = MediaType.APPLICATION_JSON_VALUE, produces = "application/pdf")
    public ResponseEntity<byte[]> downloadOcrSummary(@RequestBody Map<String, String> fields) throws Exception {

        byte[] pdf = PdfSummaryBuilder.build(fields);

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=ocr-summary.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
