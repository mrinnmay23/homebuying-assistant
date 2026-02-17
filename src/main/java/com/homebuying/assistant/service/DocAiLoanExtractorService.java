package com.homebuying.assistant.service;

import com.google.cloud.documentai.v1.Document;
import com.google.cloud.documentai.v1.Document.Entity;
import com.google.cloud.documentai.v1.DocumentProcessorServiceClient;
import com.google.cloud.documentai.v1.ProcessRequest;
import com.google.cloud.documentai.v1.RawDocument;
import com.google.protobuf.ByteString;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class DocAiLoanExtractorService {

    // If you hate properties, hardcode these four and delete @Value:
//    @Value("${docai.project-id:homebuying-assistant-3}")
  //  @Value("${docai.project-id:homebuying-assistant-4}")
//    @Value("${docai.loan.processor-id}")
//    private String projectId;

    @Value("${docai.project-id}")
    private String projectId;


    @Value("${docai.location:us}")
    private String location; // us or eu

    //@Value("${docai.processor-id:edeb273820a16ef8}")
    @Value("${docai.loan.processor-id}")
    private String processorId;

    // Optional; if blank -> use the default deployed version
    @Value("${docai.processor-version:}")
    private String processorVersion;

    /**
     * Call your custom Loan Estimate extractor and return fields.
     * No Spring beans; client is created per call (like your PdfService).
     */
    public Map<String, String> extract(byte[] bytes, String mime) throws IOException {
        if (mime == null || mime.isBlank()) mime = "application/pdf";

        // Build the resource name. If you set a specific version, call that version path.
        final String name = (processorVersion == null || processorVersion.isBlank())
                ? String.format("projects/%s/locations/%s/processors/%s", projectId, location, processorId)
                : String.format("projects/%s/locations/%s/processors/%s/processorVersions/%s",
                projectId, location, processorId, processorVersion);

        // Create client on the fly (uses GOOGLE_APPLICATION_CREDENTIALS)
        try (DocumentProcessorServiceClient client = DocumentProcessorServiceClient.create()) {

            RawDocument raw = RawDocument.newBuilder()
                    .setContent(ByteString.copyFrom(bytes))
                    .setMimeType(mime)
                    .build();

            ProcessRequest req = ProcessRequest.newBuilder()
                    .setName(name)
                    .setRawDocument(raw)
                    .build();

            Document doc = client.processDocument(req).getDocument();

            // Custom processors put results in Entities (most reliable)
            Map<String, String> out = new LinkedHashMap<>();
            for (Entity ent : doc.getEntitiesList()) {
                String key = ent.getType();                       // schema field name, e.g. "loan_amount"
                String rawVal = safe(ent.getMentionText());       // visible text span
                String normVal = ent.hasNormalizedValue()
                        ? safe(ent.getNormalizedValue().getText()) // normalized text when available
                        : "";

                if (!key.isBlank()) {
                    out.put(key, rawVal);
                    if (!normVal.isBlank()) {
                        out.put("_norm." + key, normVal);          // keep normalized alongside raw
                    }
                }
            }

            // Fallback: if your custom model output is on form fields (unlikely), also harvest them
            if (out.isEmpty()) {
                for (var page : doc.getPagesList()) {
                    for (var ff : page.getFormFieldsList()) {
                        String nameText = safe(ff.getFieldName().getTextAnchor().getContent());
                        String valText  = safe(ff.getFieldValue().getTextAnchor().getContent());
                        if (!nameText.isBlank()) {
                            out.put(slug(nameText), valText);
                        }
                    }
                }
            }

            // Add chat-friendly aliases if present
            alias(out, "loan_amount",         "loanAmount");
            alias(out, "interest_rate",       "interestRate");
            alias(out, "loan_term_years",     "termYears");
            alias(out, "estimated_closing_costs", "fees");

            return out;
        }
    }

    private static String safe(String s) { return s == null ? "" : s.trim(); }

    private static String slug(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
    }

    private static void alias(Map<String,String> out, String schemaKey, String chatKey) {
        String norm = out.get("_norm." + schemaKey);
        String raw  = out.get(schemaKey);
        if (norm != null && !norm.isBlank()) out.put("_norm." + chatKey, norm);
        if (raw  != null && !raw.isBlank())  out.put(chatKey, raw);
    }
}
