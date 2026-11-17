package edu.polyu.comp5511.hotelselection.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class HotelLlmPropertiesTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void failsFastWhenRequiredModelConfigurationIsMissing() {
        contextRunner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void acceptsCompleteOpenAiCompatibleConfiguration() {
        contextRunner.withPropertyValues(
                "hotel.llm.base-url=https://example.invalid",
                "hotel.llm.api-key=test-key",
                "hotel.llm.model=test-model")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(HotelLlmProperties.class)
    static class PropertiesConfiguration {
    }
}
