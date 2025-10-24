package com.homebuying.assistant.service;

import com.google.cloud.documentai.v1.DocumentProcessorServiceClient;
import com.google.cloud.documentai.v1.ProcessRequest;
import com.google.cloud.documentai.v1.RawDocument;
import com.google.cloud.documentai.v1.Document;
import com.google.protobuf.ByteString;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;


import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class PdfService {
    private static final String PROCESSOR_NAME =
      //      "projects/508375352782/locations/us/processors/c6ab4b25d95b23ac";
            "projects/558159150387/locations/us/processors/de92dcd624cf3ce5";

    public Map<String,String> parseLoanEstimate(MultipartFile pdf) throws IOException {
        try (var client = DocumentProcessorServiceClient.create()) {

            ByteString content = ByteString.copyFrom(pdf.getBytes());
            RawDocument raw = RawDocument.newBuilder()
                    .setContent(content)
                    .setMimeType("application/pdf")
                    .build();
            ProcessRequest req = ProcessRequest.newBuilder()
                    .setName(PROCESSOR_NAME)
                    .setRawDocument(raw)
                    .build();


            Document doc = client.processDocument(req).getDocument();


//            Map<String,String> fields = new HashMap<>();
//            for (var page : doc.getPagesList()) {
//                for (var ff : page.getFormFieldsList()) {
//
//                    String name = ff.getFieldName().getTextAnchor().getContent();
//                    String value = ff.getFieldValue().getTextAnchor().getContent();
//                    fields.put(name, value);
//                }
//            }
//            return fields;
//        }
//    }

            Map<String,String> fields = new LinkedHashMap<>();
            for (var page : doc.getPagesList()) {
                for (var ff : page.getFormFieldsList()) {
                    String name = ff.getFieldName().getTextAnchor().getContent();
                    String value = ff.getFieldValue().getTextAnchor().getContent();
                    if (name != null && !name.isBlank()) fields.put(name.trim(), value == null ? "" : value.trim());
                }
            }
            return fields;
        }
    }

    /** Simple gate: if any of the 3 core fields are missing → fallback advisable. */
    public boolean shouldFallbackToGemini(Map<String,String> docai) {
        if (docai == null || docai.isEmpty()) return true;
        return !(hasAny(docai, "Loan Amount","loan amount","Amount Financed","amount financed") &&
                hasAny(docai, "Interest Rate","interest rate","Rate","rate","APR","apr") &&
                hasAny(docai, "Loan Term","loan term","Years","years","Term","term"));
    }

    private static boolean hasAny(Map<String,String> m, String... keys) {
        for (String k : keys) {
            for (String existing : m.keySet()) {
                if (existing.equalsIgnoreCase(k)) return true;
            }
        }
        return false;
    }
}


