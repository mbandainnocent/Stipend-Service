package com.stipend.service.kafka.config;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.KafkaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    void failedDeadLetterSendIsNotReportedAsSuccessfulRecovery() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<Object, Object> kafkaTemplate = mock(KafkaTemplate.class);
        @SuppressWarnings("unchecked")
        CompletableFuture<org.springframework.kafka.support.SendResult<Object, Object>> failedSend =
                (CompletableFuture<org.springframework.kafka.support.SendResult<Object, Object>>)
                        (CompletableFuture<?>) CompletableFuture.failedFuture(
                                new KafkaException("DLT broker unavailable")
                        );
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(failedSend);

        var errorHandler = new KafkaRetryConfig().kafkaErrorHandler(
                kafkaTemplate,
                0,
                0
        );
        ConsumerRecord<String, Object> failedRecord = new ConsumerRecord<>(
                "attendance-events",
                0,
                12L,
                "attendance-key",
                "attendance-value"
        );
        @SuppressWarnings("unchecked")
        Consumer<String, Object> consumer = mock(Consumer.class);
        MessageListenerContainer container = mock(MessageListenerContainer.class);

        boolean handled = errorHandler.handleOne(
                new IllegalStateException("listener failed"),
                failedRecord,
                consumer,
                container
        );

        assertThat(handled).isFalse();
        verify(kafkaTemplate).send(any(ProducerRecord.class));
    }
}
