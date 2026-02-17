package com.homebuying.assistant.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.homebuying.assistant.model.VideoLabelsResponse;
import com.homebuying.assistant.service.VideoDamageService;
import com.homebuying.assistant.service.VideoIntelligenceRestService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/video")
public class VideoController {

    private final VideoIntelligenceRestService service;
    private final VideoDamageService damageService;


    public VideoController(VideoIntelligenceRestService service, VideoDamageService damageService) {
        this.service = service;
        this.damageService = damageService;
    }





//    @PostMapping(value = "/labels", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
//    public JsonNode labels(@RequestParam("file") MultipartFile file) throws Exception {
//        if (file.isEmpty()) throw new IllegalArgumentException("No file uploaded.");
//
//        // Keep it small for Option 1
//        long max = 25L * 1024 * 1024;
//        if (file.getSize() > max) throw new IllegalArgumentException("Keep video under 25MB for byte upload MVP.");
//
//        return service.analyzeLabels(file);
//    }

    @PostMapping(value = "/labels", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public VideoLabelsResponse labels(@RequestParam("file") MultipartFile file) throws Exception {
        return service.analyzeLabelsToDto(file);
    }

    @PostMapping(value = "/damage", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> damage(@RequestParam("file") MultipartFile file) throws Exception {
        return damageService.checkDamage(file);
    }

}
