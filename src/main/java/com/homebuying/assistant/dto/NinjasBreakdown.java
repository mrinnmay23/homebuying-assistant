package com.homebuying.assistant.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record NinjasBreakdown(
        double total,
        double mortgage,
        @JsonProperty("property_tax") double propertyTax,
        double hoa,
        @JsonProperty("home_ins") double homeInsurance
) {}
