package com.homebuying.assistant.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
public class PdfController {

    @PostMapping(value = "/api/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadPdf(
            @RequestParam("file") MultipartFile file
    ) {
        // For now, just return filename and size
        return ResponseEntity.ok(Map.of(
                "filename", file.getOriginalFilename(),
                "size", file.getSize() + " bytes"
        ));
    }
}
