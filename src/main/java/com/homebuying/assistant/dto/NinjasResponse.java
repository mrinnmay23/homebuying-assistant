package com.homebuying.assistant.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NinjasResponse(
        @JsonProperty("monthly_payment") NinjasBreakdown monthly,
        @JsonProperty("annual_payment")  NinjasBreakdown annual,
        @JsonProperty("total_interest_paid") double totalInterestPaid
) {

}
