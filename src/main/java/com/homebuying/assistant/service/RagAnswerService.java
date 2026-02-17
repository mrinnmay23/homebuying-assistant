package com.homebuying.assistant.service;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RagAnswerService {
    private final RagService rag;
    private final ChatService chat; // you already have this

    public RagAnswerService(RagService rag, ChatService chat){
        this.rag = rag; this.chat = chat;
    }

    public record RagAnswer(String reply, List<Map<String,Object>> sources) {}

    public String answerFromLibrary(String question) throws Exception {
        var hits = rag.retrieveTop(question, 20);
        if (hits == null || hits.isEmpty()) {
            return "I don't have that in my library.";
        }

        String context = hits.stream()
                .map(h -> h.chunk.text)
                .collect(Collectors.joining("\n\n----\n\n"));

//        String prompt =
//                "Answer ONLY from the CONTEXT below. If the answer is not present, say 'I don't have that in my library.'\n\n"
//                        + "CONTEXT:\n" + context + "\n\n"
//                        + "QUESTION: " + question + "\n"
//                        + "Answer:";
        String prompt =
                "Answer ONLY from the CONTEXT below. If the answer is present, quote it. " +
                        "If it’s truly not present, say 'I don't have that in my library.'\n\n" +
                        "CONTEXT:\n" + context + "\n\n" +
                        "QUESTION: " + question + "\n" +
                        "Answer:";

        return chat.ask(prompt); // reuse your Gemini text call
    }





    public RagAnswer answerFromLibraryRich(String question) throws Exception {
        var hits = rag.retrieveTop(question, 6);

        if (hits == null || hits.isEmpty()) {
            return new RagAnswer("I don't have that in my library.", List.of());
        }

        String context = hits.stream().map(h -> h.chunk.text)
                .collect(Collectors.joining("\n\n----\n\n"));

        String prompt =
                "Answer ONLY from the CONTEXT below. If the answer is not present, say 'I don't have that in my library.'\n\n"
                        + "CONTEXT:\n" + context + "\n\n"
                        + "QUESTION: " + question + "\n"
                        + "Answer:";

        String reply = chat.ask(prompt);

        List<Map<String, Object>> sources = hits.stream().map(h -> {
            Map<String, Object> m = new HashMap<>();
            m.put("file", h.chunk.document.filename);
            m.put("page", h.chunk.pageStart);
            return m;
        }).collect(Collectors.toList());

        return new RagAnswer(reply, sources);
    }



}
//@Service
//public class RagAnswerService {
//    private final RagService rag;
//    private final ChatService chat;
//
//    public RagAnswerService(RagService rag, ChatService chat){
//        this.rag = rag; this.chat = chat;
//    }
//
//    public String answerFromLibrary(String question) throws Exception {
//        // take more context (e.g., 12–16)
//        var hits = rag.retrieveTop(question, 16);
//
//        // Build a richer, source-tagged context
//        StringBuilder sb = new StringBuilder();
//        int i = 1;
//        for (var h : hits) {
//            sb.append("### Source ").append(i++).append(" (")
//                    .append(h.chunk.document.filename).append(", score=")
//                    .append(String.format("%.3f", h.score)).append(")\n")
//                    .append(h.chunk.text).append("\n\n");
//        }
//        String context = sb.toString();
//
//        // Softer guardrail: ask to extract whatever is present;
//        // only if clearly missing, say the fallback.
//        String prompt =
//                """
//                You are a precise extraction assistant for mortgage documents and property brochures.
//                Use ONLY the CONTEXT to answer. If the exact number/phrase is present, quote it.
//                If the answer is partially present (e.g., lists, bullet points), extract what's available.
//                ONLY if the context truly doesn't contain relevant info, reply exactly:
//                "I don't have that in my library."
//
//                QUESTION:
//                """ + question + "\n\n" +
//                        "CONTEXT:\n" + context + "\n\n" +
//                        "Answer succinctly. If quoting figures, include the units and the exact wording from CONTEXT.";
//
//        return chat.ask(prompt);
//    }
//}
