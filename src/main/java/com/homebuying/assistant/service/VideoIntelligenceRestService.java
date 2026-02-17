package com.homebuying.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.homebuying.assistant.dto.VideoChapterDto;
import com.homebuying.assistant.dto.VideoLabelDto;
import com.homebuying.assistant.dto.VideoSegmentDto;
import com.homebuying.assistant.model.VideoLabelsResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class VideoIntelligenceRestService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public VideoIntelligenceRestService(WebClient.Builder builder) {
        this.webClient = builder
                .baseUrl("https://videointelligence.googleapis.com")
                .build();
    }

    /**
     * Calls Google Video Intelligence REST API and returns the raw "response" JSON
     * (contains annotationResults).
     */
    public JsonNode analyzeLabels(MultipartFile file) throws Exception {
        byte[] bytes = file.getBytes();
        String base64Video = Base64.getEncoder().encodeToString(bytes);

        String accessToken = getAccessToken();

        // ✅ Ask for SHOT + FRAME labels too (many videos return labels there)
        Map<String, Object> body = Map.of(
                "inputContent", base64Video,
            //    "features", new String[]{"LABEL_DETECTION"},
                "features", new String[]{"LABEL_DETECTION", "SHOT_CHANGE_DETECTION"},

                "videoContext", Map.of(
                        "labelDetectionConfig", Map.of(
                                "labelDetectionMode", "SHOT_AND_FRAME_MODE"
                        )
                )
        );

        String opResponse = webClient.post()
                .uri("/v1/videos:annotate")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(60));

        if (opResponse == null || opResponse.isBlank()) {
            throw new RuntimeException("Empty response from videos:annotate");
        }

        JsonNode opJson = objectMapper.readTree(opResponse);
        String opName = opJson.path("name").asText(null); // operations/xxxx

        if (opName == null || opName.isBlank()) {
            throw new RuntimeException("Could not read operation name from response: " + opResponse);
        }

        // Poll until done
        for (int i = 0; i < 120; i++) { // up to ~120 seconds
            String pollResp = webClient.get()
                    .uri("/v1/" + opName)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(30));

            if (pollResp == null || pollResp.isBlank()) {
                Thread.sleep(1000);
                continue;
            }

            JsonNode pollJson = objectMapper.readTree(pollResp);

            if (pollJson.path("done").asBoolean(false)) {
                if (pollJson.has("error")) {
                    throw new RuntimeException("Video Intelligence error: " + pollJson.get("error").toString());
                }
                // ✅ The final actual response is here:
                return pollJson.get("response");
            }

            Thread.sleep(1000);
        }

        throw new RuntimeException("Timed out waiting for Video Intelligence operation to finish.");
    }

    private String getAccessToken() throws Exception {
        GoogleCredentials creds = GoogleCredentials.getApplicationDefault()
                .createScoped("https://www.googleapis.com/auth/cloud-platform");
        creds.refreshIfExpired();
        return creds.getAccessToken().getTokenValue();
    }

    /**
     * Converts Google raw JSON into your DTO shape expected by the UI:
     * { fileName, sizeBytes, labels: [ {label, categories, segments[]} ] }
     */
    public VideoLabelsResponse analyzeLabelsToDto(MultipartFile file) throws Exception {
        JsonNode googleResponse = analyzeLabels(file);

        VideoLabelsResponse out = new VideoLabelsResponse();
        out.fileName = file.getOriginalFilename();
        out.sizeBytes = file.getSize();

        JsonNode resultsArr = googleResponse.get("annotationResults");
        if (resultsArr == null || !resultsArr.isArray() || resultsArr.size() == 0) {
            return out;
        }

        JsonNode results = resultsArr.get(0);

        // ✅ shotAnnotations = chapters
        JsonNode shots = results.get("shotAnnotations");
        if (shots != null && shots.isArray()) {
            for (JsonNode sh : shots) {
                double start = parseSeconds(sh.path("startTimeOffset").asText(null));
                double end = parseSeconds(sh.path("endTimeOffset").asText(null));
                out.chapters.add(new VideoChapterDto(start, end));

            }
        }


        // Merge labels by name
        Map<String, VideoLabelDto> map = new LinkedHashMap<>();

        // segmentLabelAnnotations
        readLabelArray(results.get("segmentLabelAnnotations"), map, "segments");

        // shotLabelAnnotations (IMPORTANT)
        readLabelArray(results.get("shotLabelAnnotations"), map, "segments");

        // frameLabelAnnotations (IMPORTANT)
        readLabelArray(results.get("frameLabelAnnotations"), map, "frames");

        out.labels.addAll(map.values());

        double MIN_CONF = 0.60;

        out.labels.removeIf(l ->
                l.segments.removeIf(s -> s.confidence < MIN_CONF) || l.segments.isEmpty()
        );


        return out;
    }

    private void readLabelArray(JsonNode arr, Map<String, VideoLabelDto> map, String mode) {
        if (arr == null || !arr.isArray()) return;

        for (JsonNode la : arr) {
            String label = safeText(la.path("entity").path("description"));
            if (label == null || label.isBlank()) continue;

            VideoLabelDto dto = map.computeIfAbsent(label, VideoLabelDto::new);

            // categories
            JsonNode cats = la.path("categoryEntities");
            if (cats.isArray()) {
                for (JsonNode c : cats) {
                    String cat = safeText(c.path("description"));
                    if (cat != null && !cat.isBlank() && !dto.categories.contains(cat)) {
                        dto.categories.add(cat);
                    }
                }
            }

            if ("frames".equals(mode)) {
                // frames: [{ timeOffset: "1.200s", confidence: 0.6 }]
                JsonNode frames = la.path("frames");
                if (frames.isArray()) {
                    for (JsonNode f : frames) {
                        double t = parseSeconds(f.path("timeOffset").asText(null));
                        float conf = (float) f.path("confidence").asDouble(0);
                        dto.segments.add(new VideoSegmentDto(t, t, conf));
                    }
                }
            } else {
                // segments: [{ segment: { startTimeOffset:"0s", endTimeOffset:"3.2s" }, confidence:0.8 }]
                JsonNode segs = la.path("segments");
                if (segs.isArray()) {
                    for (JsonNode s : segs) {
                        double start = parseSeconds(s.path("segment").path("startTimeOffset").asText(null));
                        double end = parseSeconds(s.path("segment").path("endTimeOffset").asText(null));
                        float conf = (float) s.path("confidence").asDouble(0);
                        dto.segments.add(new VideoSegmentDto(start, end, conf));
                    }
                }
            }
        }
    }

    private String safeText(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) return null;
        return n.asText();
    }

    // Google returns "1.200s" or "0s"
    private double parseSeconds(String s) {
        if (s == null) return 0;
        s = s.trim();
        if (s.endsWith("s")) s = s.substring(0, s.length() - 1);
        try {
            return Double.parseDouble(s);
        } catch (Exception e) {
            return 0;
        }
    }
}
