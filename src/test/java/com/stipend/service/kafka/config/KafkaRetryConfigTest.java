package com.stipend.service.kafka.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.assertj.core.api.Assertions.assertThat;

class KafkaRetryConfigTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(KafkaAutoConfiguration.class))
                    .withUserConfiguration(KafkaRetryConfig.class);

    @Test
    void producerFactoryAndKafkaTemplateAreAvailableWithBootAutoConfiguration() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ProducerFactory.class);
            assertThat(context).hasSingleBean(KafkaTemplate.class);
        });
    }

    @Test
    void valueSerializerPreservesRawBytesForDeadLetterMessages() {
        DelegatingByTypeSerializer serializer =
                KafkaRetryConfig.createValueSerializer();
        byte[] malformedPayload = "{".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        assertArrayEquals(
                malformedPayload,
                serializer.serialize("registration-events.DLT", malformedPayload)
        );
        serializer.close();
    }
}
