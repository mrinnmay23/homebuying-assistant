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


    public Map<String,String> extractSlots(String userText, Intent intent) {
        switch (intent) {
            case GET_QUOTES: {
                // e.g. "720 credit score"
                Matcher m = Pattern.compile("(\\d{3})\\s*credit score", Pattern.CASE_INSENSITIVE)
                        .matcher(userText);
                if (m.find()) {
                    return Map.of("creditScore", m.group(1));
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

            case REFINANCE_CHECK: {
                // e.g. "refinance if rates drop from 4.0% to 3.5% on $250k over 20 years"
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
                break;
            }

            case AGENT_LOOKUP: {
                // e.g. "connect me to an agent in Seattle" or "agent id 42"
                Matcher byCity = Pattern.compile("agent.*in\\s+([A-Za-z ]+)", Pattern.CASE_INSENSITIVE)
                        .matcher(userText);
                if (byCity.find()) {
                    return Map.of("city", byCity.group(1).trim());
                }
                Matcher byId = Pattern.compile("agent id\\s*(\\d+)", Pattern.CASE_INSENSITIVE)
                        .matcher(userText);
                if (byId.find()) {
                    return Map.of("agentId", byId.group(1));
                }
                break;
            }

            default:
                // GREETING, PDF_UPLOAD, FALLBACK → no slots needed
                break;
        }

        return Collections.emptyMap();
    }
}


