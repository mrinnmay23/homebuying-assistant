package com.homebuying.assistant.service;

import com.homebuying.assistant.dto.NinjasResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

@Service
public class ExternalMortgageService {
    private final RestTemplate rest = new RestTemplate();
    private final String baseUrl;
    private final String apiKey;

    public ExternalMortgageService(
            @Value("${ninjas.mortgage.url}") String baseUrl,
            @Value("${ninjas.api.key}")      String apiKey) {
        this.baseUrl = baseUrl;
        this.apiKey  = apiKey;
    }

    public NinjasResponse calculate(double principal, double annualRate, int years) {
        String url = String.format(
                "%s?loan_amount=%.2f&interest_rate=%.6f&duration_years=%d",
                baseUrl, principal, annualRate, years);

        RequestEntity<Void> req = RequestEntity.get(URI.create(url))
                .header("X-Api-Key", apiKey)
                .accept(MediaType.APPLICATION_JSON)
                .build();

        return rest.exchange(req, NinjasResponse.class).getBody();
    }
}