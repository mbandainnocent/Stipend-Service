package com.stipend.service.kafka.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.support.SendResult;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Configuration
public class KafkaRetryConfig {
    private static final Logger dltLogger = LoggerFactory.getLogger(KafkaRetryConfig.class);
    private static final String DLT_PUBLISH_METRIC = "stipend.kafka.dlt.publish";

    @Bean
    public ProducerFactory<Object, Object> producerFactory(
            KafkaProperties kafkaProperties) {
        Map<String, Object> producerProperties =
                new HashMap<>(kafkaProperties.buildProducerProperties(null));
        producerProperties.put(
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class
        );
        producerProperties.put(
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                DelegatingByTypeSerializer.class
        );

        return new DefaultKafkaProducerFactory<>(
                producerProperties,
                null,
                createValueSerializer()
        );
    }

    static DelegatingByTypeSerializer createValueSerializer() {
        Map<Class<?>, Serializer<?>> valueSerializers = new LinkedHashMap<>();
        valueSerializers.put(byte[].class, new ByteArraySerializer());
        valueSerializers.put(Object.class, new JsonSerializer<>());
        return new DelegatingByTypeSerializer(valueSerializers, true);
    }

    @Bean
    public KafkaTemplate<Object, Object> kafkaTemplate(
            ProducerFactory<Object, Object> kafkaProducerFactory) {
        return new KafkaTemplate<>(kafkaProducerFactory);
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            KafkaTemplate<Object, Object> kafkaTemplate,
            MeterRegistry meterRegistry,
            @Value("${app.kafka.retry.interval-ms:1000}") long retryIntervalMs,
            @Value("${app.kafka.retry.max-retries:2}") long maxRetries) {
        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(kafkaTemplate) {
                    @Override
                    protected void verifySendResult(
                            org.springframework.kafka.core.KafkaOperations<Object, Object> template,
                            org.apache.kafka.clients.producer.ProducerRecord<Object, Object> producerRecord,
                            CompletableFuture<SendResult<Object, Object>> result,
                            ConsumerRecord<?, ?> consumerRecord) {
                        try {
                            super.verifySendResult(template, producerRecord, result, consumerRecord);
                            meterRegistry.counter(
                                    DLT_PUBLISH_METRIC,
                                    "source_topic", consumerRecord.topic(),
                                    "outcome", "success"
                            ).increment();
                        } catch (RuntimeException exception) {
                            meterRegistry.counter(
                                    DLT_PUBLISH_METRIC,
                                    "source_topic", consumerRecord.topic(),
                                    "outcome", "failure"
                            ).increment();
                            dltLogger.error(
                                    "Failed to publish record to DLT for source topic {} partition {} offset {}",
                                    consumerRecord.topic(),
                                    consumerRecord.partition(),
                                    consumerRecord.offset(),
                                    exception
                            );
                            throw exception;
                        }
                    }
                };
        recoverer.setFailIfSendResultIsError(true);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(retryIntervalMs, maxRetries)
        );
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);
        return errorHandler;
    }
}
