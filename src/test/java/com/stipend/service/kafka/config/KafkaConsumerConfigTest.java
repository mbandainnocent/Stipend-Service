package com.stipend.service.kafka.config;

import com.stipend.service.Event.StudentRegisteredEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaConsumerConfigTest {

    @Test
    void registrationConsumerUsesConfiguredBootstrapServersAndGroup() {
        ConsumerFactory<String, StudentRegisteredEvent> consumerFactory =
                new KafkaConsumerConfig()
                        .studentRegistrationConsumerFactory("kafka:29092", "registration-group");

        assertThat(consumerFactory.getConfigurationProperties())
                .containsEntry(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:29092")
                .containsEntry(ConsumerConfig.GROUP_ID_CONFIG, "registration-group");
        assertThat(consumerFactory.getValueDeserializer())
                .isInstanceOf(ErrorHandlingDeserializer.class);
    }
}
