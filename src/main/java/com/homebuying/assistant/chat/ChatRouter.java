package com.homebuying.assistant.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.homebuying.assistant.repository.IntentRepository;
import com.homebuying.assistant.service.ChatService;
import org.springframework.stereotype.Component;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ChatRouter {

    private final ChatService chatService;
    private final IntentRepository intentRepo;
    private final ObjectMapper mapper = new ObjectMapper();

    public ChatRouter(ChatService chatService,
                      IntentRepository intentRepo) {
        this.chatService  = chatService;
        this.intentRepo   = intentRepo;
    }

    /** Classify user input into one of our defined intents. */
    public Intent classify(String userText) {
        String prompt = buildClassificationPrompt(userText);
        String raw    = chatService.ask(prompt);
        try {
            return Intent.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return Intent.FALLBACK; // fallback if LLM returns something unexpected
        }
    }

    /** Build a concise prompt listing each intent with 2 examples, then the user text. */
    private String buildClassificationPrompt(String userText) {
        StringBuilder sb = new StringBuilder();
        sb.append("Classify the user request into one of the following intents:\n");
        // For each intent, take first two sample utterances:
        for (Map.Entry<Intent,List<String>> entry : intentRepo.findAll().entrySet()) {
            Intent intent = entry.getKey();
            List<String> samples = entry.getValue();
            sb.append("- ").append(intent.name()).append(": \"")
                    .append(String.join("\" or \"",
                            samples.subList(0, Math.min(2, samples.size()))))
                    .append("\"\n");
        }
        sb.append("\nUser says: \"").append(userText).append("\"\n");
        sb.append("Answer with exactly the intent name.");
        return sb.toString();
    }

    public Map<String,String> extractGlobalSlots(String userText) {
        java.util.LinkedHashMap<String,String> out = new java.util.LinkedHashMap<>();
        if (userText == null) return out;

        // principal: "principal $320,000" / "loan amount 320000"
        Matcher p = Pattern.compile("(principal|loan amount|amount financed|amount)\\s*\\$?([\\d,]+(?:\\.\\d+)?)",
                Pattern.CASE_INSENSITIVE).matcher(userText);
        if (p.find()) out.put("principal", p.group(2));

        // rate: "rate 4.1%" / "interest 4.1%"
        Matcher r = Pattern.compile("(rate|interest)\\s*(is|=)?\\s*([\\d.]+)\\s*%",
                Pattern.CASE_INSENSITIVE).matcher(userText);
        if (r.find()) out.put("rate", r.group(3));

        // term: "term 30 years" / "use 25 yrs" / "30 years"
        Matcher t = Pattern.compile("(term|years?|yrs?)\\s*(is|=)?\\s*(\\d{1,2})",
                Pattern.CASE_INSENSITIVE).matcher(userText);
        if (t.find()) out.put("termYears", t.group(3));

        // fees: "fees $8000" / "closing costs 1.2%"
        Matcher f = Pattern.compile("(fees?|closing costs?)\\s*(are|=)?\\s*(\\$?[\\d,]+(?:\\.\\d+)?|[\\d.]+\\s*%)",
                Pattern.CASE_INSENSITIVE).matcher(userText);
        if (f.find()) out.put("fees", f.group(3));

        // credit score: "score 720" / "credit score 720"
        Matcher cs = Pattern.compile("(?:credit\\s*)?score\\s*(\\d{3})\\b",
                Pattern.CASE_INSENSITIVE).matcher(userText);
        if (cs.find()) out.put("creditScore", cs.group(1));

        return out;
    }



    public Map<String,String> extractSlots(String userText, Intent intent) {
        switch (intent) {
//            case GET_QUOTES: {
//                // e.g. "720 credit score"
//                Matcher m = Pattern.compile("(\\d{3})\\s*credit score", Pattern.CASE_INSENSITIVE)
//                        .matcher(userText);
//                if (m.find()) {
//                    return Map.of("creditScore", m.group(1));
//                }
//                break;
//            }

            case GET_QUOTES: {
                // match “score 700”, “credit score 700”, or a bare 3-digit number
                Matcher m = Pattern.compile(
                        "(?:credit\\s*)?score\\s*(\\d{3})|\\b(\\d{3})\\b",
                        Pattern.CASE_INSENSITIVE
                ).matcher(userText);
                if (m.find()) {
                    String s = (m.group(1) != null) ? m.group(1) : m.group(2);
                    return Map.of("creditScore", s);
                }
                break;
            }


            case LOAN_CALCULATOR: {
                // e.g. "300000 at 4.2% over 30 years"
                Matcher m = Pattern.compile(
                        "\\$?(\\d+(?:\\.\\d+)?)\\s*(?:dollars)?\\s*(?:at)?\\s*([\\d.]+)%\\s*over\\s*(\\d+)\\s*years",
                        Pattern.CASE_INSENSITIVE
                ).matcher(userText);
                if (m.find()) {
                    return Map.of(
                            "principal", m.group(1),
                            "rate",      m.group(2),
                            "termYears", m.group(3)
                    );
                }
                break;
            }

            case OFFER_SCORE: {
                // e.g. "my rate is 3.5% and fees are 1.2%"
                Matcher m = Pattern.compile(
                        "rate\\s*(?:is)?\\s*([\\d.]+)%.*fees\\s*(?:are)?\\s*([\\d.]+)",
                        Pattern.CASE_INSENSITIVE
                ).matcher(userText);
                if (m.find()) {
                    return Map.of(
                            "rate", m.group(1),
                            "fees", m.group(2)
                    );
                }
                break;
            }

            case AMORTIZATION: {
                // same slots as loan_calculator
                Matcher m = Pattern.compile(
                        "\\$?(\\d+(?:\\.\\d+)?)\\s*at\\s*([\\d.]+)%\\s*over\\s*(\\d+)\\s*years",
                        Pattern.CASE_INSENSITIVE
                ).matcher(userText);
                if (m.find()) {
                    return Map.of(
                            "principal", m.group(1),
                            "rate",      m.group(2),
                            "termYears", m.group(3)
                    );
                }
                break;
            }

//            case REFINANCE_CHECK: {
//                // e.g. "refinance if rates drop from 4.0% to 3.5% on $250k over 20 years"
//                Matcher m = Pattern.compile(
//                        "from\\s*([\\d.]+)%\\s*to\\s*([\\d.]+)%.*\\$?(\\d+(?:\\.\\d+)?).*?(\\d+)\\s*years",
//                        Pattern.CASE_INSENSITIVE
//                ).matcher(userText);
//                if (m.find()) {
//                    return Map.of(
//                            "currentRate", m.group(1),
//                            "newRate",     m.group(2),
//                            "principal",   m.group(3),
//                            "termYears",   m.group(4)
//                    );
//                }
//                break;
//            }

            case REFINANCE_CHECK: {
                // Pattern A: "from 4.0% to 3.5% on $250k over 20 years"
                Matcher m = Pattern.compile(
                        "from\\s*([\\d.]+)%\\s*to\\s*([\\d.]+)%.*\\$?(\\d+(?:\\.\\d+)?).*?(\\d+)\\s*years",
                        Pattern.CASE_INSENSITIVE
                ).matcher(userText);
                if (m.find()) {
                    return Map.of(
                            "currentRate", m.group(1),
                            "newRate",     m.group(2),
                            "principal",   m.group(3),
                            "termYears",   m.group(4)
                    );
                }

                // Pattern B: "refinance to 3.9%" / "refi to 3.9%" / "can get 3.9%"
                Matcher m2 = Pattern.compile(
                        "(?:refinance|refi|to|get|at)\\s*([\\d.]+)\\s*%",
                        Pattern.CASE_INSENSITIVE
                ).matcher(userText);
                if (m2.find()) {
                    return Map.of("newRate", m2.group(1));
                }
                break;
            }


//            case AGENT_LOOKUP: {
//                // e.g. "connect me to an agent in Seattle" or "agent id 42"
//                Matcher byCity = Pattern.compile("agent.*in\\s+([A-Za-z ]+)", Pattern.CASE_INSENSITIVE)
//                        .matcher(userText);
//                if (byCity.find()) {
//                    return Map.of("city", byCity.group(1).trim());
//                }
//                Matcher byId = Pattern.compile("agent id\\s*(\\d+)", Pattern.CASE_INSENSITIVE)
//                        .matcher(userText);
//                if (byId.find()) {
//                    return Map.of("agentId", byId.group(1));
//                }
//                break;
//            }
//            case AGENT_LOOKUP: {
//                Matcher byKeywordId = Pattern.compile("\\bagent\\s*(\\d+)\\b", Pattern.CASE_INSENSITIVE)
//                        .matcher(userText);
//                if (byKeywordId.find()) {
//                    return Map.of("agentId", byKeywordId.group(1));
//                }
//                Matcher byCity = Pattern.compile("agent.*in\\s+([A-Za-z ]+)", Pattern.CASE_INSENSITIVE)
//                        .matcher(userText);
//                if (byCity.find()) {
//                    return Map.of("city", byCity.group(1).trim());
//                }
//                Matcher byId = Pattern.compile("\\bagent id\\s*(\\d+)\\b", Pattern.CASE_INSENSITIVE)
//                        .matcher(userText);
//                if (byId.find()) {
//                    return Map.of("agentId", byId.group(1));
//                }
//                break;
//            }

            case AGENT_LOOKUP: {
                // agent 5 / agent id 5
                Matcher byId = Pattern.compile("\\bagent\\s*(?:id\\s*)?(\\d+)\\b", Pattern.CASE_INSENSITIVE)
                        .matcher(userText);
                if (byId.find()) return Map.of("agentId", byId.group(1));

                // agent bob / agent bob jones
                Matcher byName = Pattern.compile("\\bagent\\s+([A-Za-z][A-Za-z .\\-']{1,40})\\b",
                        Pattern.CASE_INSENSITIVE).matcher(userText);
                if (byName.find()) return Map.of("agentName", byName.group(1).trim());

                break;
            }



            default:
                // GREETING, PDF_UPLOAD, FALLBACK → no slots needed
                break;
        }

        return Collections.emptyMap();
    }
}


