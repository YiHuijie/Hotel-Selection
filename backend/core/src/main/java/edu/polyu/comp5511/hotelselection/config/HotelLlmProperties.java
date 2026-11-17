package edu.polyu.comp5511.hotelselection.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "hotel.llm")
public record HotelLlmProperties(
        @NotBlank String baseUrl,
        @NotBlank String apiKey,
        @NotBlank String model) {
}
