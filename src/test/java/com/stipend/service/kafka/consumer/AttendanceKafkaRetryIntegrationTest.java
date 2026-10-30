package com.stipend.service.kafka.consumer;

import com.stipend.service.Event.AttendanceEvent;
import com.stipend.service.kafka.config.KafkaRetryConfig;
import com.stipend.service.service.AttendanceEventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@EmbeddedKafka(
        partitions = 1,
        topics = {
                "attendance-retry-test",
                "attendance-retry-test.DLT",
                "attendance-malformed-test",
                "attendance-malformed-test.DLT",
                "attendance-invalid-test",
                "attendance-invalid-test.DLT"
        }
)
@SpringJUnitConfig
class AttendanceKafkaRetryIntegrationTest {

    private static final String VALID_EVENT_JSON = """
            {
              "eventId": "50000000-0000-0000-0000-000000000001",
              "attendanceId": "60000000-0000-0000-0000-000000000001",
              "studentId": "20000000-0000-0000-0000-000000000001",
              "programId": "30000000-0000-0000-0000-000000000001",
              "cohortId": "40000000-0000-0000-0000-000000000001",
              "attendanceStatus": "PRESENT",
              "attendanceDate": "2026-10-12",
              "timestamp": "2026-10-12T12:00:00Z",
              "version": 1
            }
            """;

    @Autowired
    private EmbeddedKafkaBroker broker;
    private KafkaTemplate<String, String> sourceTemplate;
    private KafkaTemplate<Object, Object> dltTemplate;
    private DefaultKafkaProducerFactory<String, String> sourceProducerFactory;
    private DefaultKafkaProducerFactory<Object, Object> dltProducerFactory;
    private ConcurrentMessageListenerContainer<String, AttendanceEvent> container;
    private Consumer<String, byte[]> dltConsumer;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        Map<String, Object> producerProperties =
                new HashMap<>(KafkaTestUtils.producerProps(broker));
        producerProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProperties.put(
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class
        );
        sourceProducerFactory = new DefaultKafkaProducerFactory<>(producerProperties);
        sourceTemplate = new KafkaTemplate<>(sourceProducerFactory);

        KafkaProperties kafkaProperties = new KafkaProperties();
        kafkaProperties.setBootstrapServers(List.of(broker.getBrokersAsString()));
        KafkaRetryConfig retryConfig = new KafkaRetryConfig();
        dltProducerFactory = (DefaultKafkaProducerFactory<Object, Object>)
                retryConfig.producerFactory(kafkaProperties);
        dltTemplate = retryConfig.kafkaTemplate(dltProducerFactory);
    }

    @AfterEach
    void stopResources() {
        if (container != null) {
            container.stop();
        }
        if (dltConsumer != null) {
            dltConsumer.close();
        }
        sourceProducerFactory.destroy();
        dltProducerFactory.destroy();
    }

    @Test
    void retriesTransientListenerFailuresUntilProcessingSucceeds() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch attemptLatch = new CountDownLatch(3);
        AttendanceEventService service = event -> {
            int currentAttempt = attempts.incrementAndGet();
            attemptLatch.countDown();
            if (currentAttempt < 3) {
                throw new IllegalStateException("temporary persistence failure");
            }
        };
        startAttendanceListener("attendance-retry-test", service);
        sourceTemplate.send("attendance-retry-test", "attendance-key", VALID_EVENT_JSON).get();

        assertThat(attemptLatch.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    void sendsMalformedJsonToDeadLetterTopicWithoutCallingService() throws Exception {
        AtomicInteger serviceCalls = new AtomicInteger();
        startDltConsumer("attendance-malformed-test.DLT");
        startAttendanceListener(
                "attendance-malformed-test",
                event -> serviceCalls.incrementAndGet()
        );
        byte[] malformedJson = "{".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        sourceTemplate.send(
                "attendance-malformed-test",
                "attendance-key",
                new String(malformedJson, java.nio.charset.StandardCharsets.UTF_8)
        ).get();

        ConsumerRecord<String, byte[]> deadLetter =
                KafkaTestUtils.getSingleRecord(dltConsumer, "attendance-malformed-test.DLT",
                        Duration.ofSeconds(10));

        assertThat(deadLetter.value()).isEqualTo(malformedJson);
        assertThat(serviceCalls.get()).isZero();
    }

    @Test
    void doesNotRetryInvalidEventAndPublishesItToDeadLetterTopic() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        startDltConsumer("attendance-invalid-test.DLT");
        startAttendanceListener("attendance-invalid-test", event -> {
            attempts.incrementAndGet();
            if (event.attendanceId() == null) {
                throw new IllegalArgumentException("attendanceId is required");
            }
        });
        String invalidEvent = VALID_EVENT_JSON.replace(
                "\"attendanceId\": \"60000000-0000-0000-0000-000000000001\"",
                "\"attendanceId\": null"
        );
        sourceTemplate.send("attendance-invalid-test", "attendance-key", invalidEvent).get();

        ConsumerRecord<String, byte[]> deadLetter = KafkaTestUtils.getSingleRecord(
                dltConsumer,
                "attendance-invalid-test.DLT",
                Duration.ofSeconds(10)
        );

        assertThat(new ObjectMapper().readTree(deadLetter.value()).get("attendanceId").isNull())
                .isTrue();
        assertThat(attempts.get()).isEqualTo(1);
    }

    private void startAttendanceListener(
            String topic,
            AttendanceEventService attendanceEventService
    ) throws InterruptedException {
        Map<String, Object> consumerProperties =
                new HashMap<>(KafkaTestUtils.consumerProps(topic + "-group", "false", broker));
        consumerProperties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        JsonDeserializer<AttendanceEvent> jsonDeserializer =
                new JsonDeserializer<>(AttendanceEvent.class);
        jsonDeserializer.addTrustedPackages("com.stipend.service.Event");
        ErrorHandlingDeserializer<AttendanceEvent> valueDeserializer =
                new ErrorHandlingDeserializer<>(jsonDeserializer);
        DefaultKafkaConsumerFactory<String, AttendanceEvent> consumerFactory =
                new DefaultKafkaConsumerFactory<>(
                        consumerProperties,
                        new StringDeserializer(),
                        valueDeserializer
                );

        ConcurrentKafkaListenerContainerFactory<String, AttendanceEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(new KafkaRetryConfig().kafkaErrorHandler(
                dltTemplate,
                0,
                2
        ));
        container = factory.createContainer(topic);
        container.getContainerProperties().setMessageListener(
                (org.springframework.kafka.listener.MessageListener<String, AttendanceEvent>)
                        record -> new AttendanceEventConsumer(attendanceEventService)
                                .consume(record.value())
        );
        container.start();
        ContainerTestUtils.waitForAssignment(container, 1);
    }

    private void startDltConsumer(String topic) {
        Map<String, Object> consumerProperties =
                new HashMap<>(KafkaTestUtils.consumerProps(topic + "-group", "false", broker));
        consumerProperties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        dltConsumer = new KafkaConsumer<>(
                consumerProperties,
                new StringDeserializer(),
                new ByteArrayDeserializer()
        );
        dltConsumer.subscribe(List.of(topic));
        dltConsumer.poll(Duration.ofMillis(100));
    }
}
