package com.homebuying.assistant.service;

import com.google.cloud.documentai.v1.DocumentProcessorServiceClient;
import com.google.cloud.documentai.v1.ProcessRequest;
import com.google.cloud.documentai.v1.RawDocument;
import com.google.cloud.documentai.v1.Document;
import com.google.protobuf.ByteString;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;


import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
public class PdfService {
//    private static final String PROCESSOR_NAME =
//      //      "projects/508375352782/locations/us/processors/c6ab4b25d95b23ac";
//       //     "projects/558159150387/locations/us/processors/de92dcd624cf3ce5";
//         "projects/677345735320/locations/us/processors/eef9fc341db7d50e";


    @Value("${docai.project-id}")
    private String projectId;

    @Value("${docai.location:us}")
    private String location;

    @Value("${docai.form.processor-id}") // <-- add this property
    private String formProcessorId;


    public Map<String,String> parseLoanEstimate(MultipartFile pdf) throws IOException {

        String processorName = String.format(
                "projects/%s/locations/%s/processors/%s",
                projectId, location, formProcessorId
        );

        try (var client = DocumentProcessorServiceClient.create()) {

            ByteString content = ByteString.copyFrom(pdf.getBytes());
            RawDocument raw = RawDocument.newBuilder()
                    .setContent(content)
                    .setMimeType("application/pdf")
                    .build();
            ProcessRequest req = ProcessRequest.newBuilder()
                    .setName(processorName)
                    .setRawDocument(raw)
                    .build();


            Document doc = client.processDocument(req).getDocument();


            Map<String,String> fields = new HashMap<>();
            for (var page : doc.getPagesList()) {
                for (var ff : page.getFormFieldsList()) {

                    String name = ff.getFieldName().getTextAnchor().getContent();
                    String value = ff.getFieldValue().getTextAnchor().getContent();
                    fields.put(name, value);
                }
            }
            return fields;
        }
    }
}


