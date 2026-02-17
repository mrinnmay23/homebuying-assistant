package com.homebuying.assistant.controller;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;


import org.apache.pdfbox.pdmodel.common.PDRectangle;

import java.io.ByteArrayOutputStream;
import java.util.Map;

public class PdfSummaryBuilder {

    public static byte[] build(Map<String,String> summary) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

                cs.setFont(bold, 16);

                cs.newLineAtOffset(50, 740);
                cs.showText("Loan Estimate Summary");
                cs.newLineAtOffset(0, -24);

                PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

                cs.setFont(regular, 11);



                int lines = 0;
                for (var e : summary.entrySet()) {
                    String line = e.getKey() + " : " + (e.getValue() == null ? "" : e.getValue());
                    cs.showText(line);
                    cs.newLineAtOffset(0, -16);
                    lines++;
                    if (lines > 35) break; // keep simple
                }

                cs.endText();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }
}
