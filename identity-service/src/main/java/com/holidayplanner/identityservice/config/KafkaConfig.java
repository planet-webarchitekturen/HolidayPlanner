package com.holidayplanner.identityservice.config;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Configuration for Identity Service.
 * 
 * Responsibilities:
 * - Configure producer for publishing domain events
 * - Configure consumer for listening to relevant events
 * - Define topics with auto-creation enabled
 * - Set serialization/deserialization strategy (JSON)
 * 
 * Topics Created:
 * - holiday-planner.identity.user-registered (from registerUser)
 * - holiday-planner.identity.user-phone-updated (from updatePhoneNumber)
 * - holiday-planner.identity.family-member-added (from addFamilyMember)
 * - holiday-planner.identity.family-member-removed (from removeFamilyMember)
 * - holiday-planner.booking.cancelled (consumed for future cascade logic)
 * - holiday-planner.payment.refunded (consumed for future notifications)
 */
@Configuration
@EnableKafka
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    // ============================================================
    // KAFKA ADMIN - Topic Management
    // ============================================================

    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return new KafkaAdmin(configs);
    }

    /**
     * Topics produced by Identity Service
     */
    @Bean
    public NewTopic userRegisteredTopic() {
        return TopicBuilder.name("holiday-planner.identity.user-registered")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic userPhoneUpdatedTopic() {
        return TopicBuilder.name("holiday-planner.identity.user-phone-updated")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic familyMemberAddedTopic() {
        return TopicBuilder.name("holiday-planner.identity.family-member-added")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic familyMemberRemovedTopic() {
        return TopicBuilder.name("holiday-planner.identity.family-member-removed")
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Topics consumed by Identity Service (from other services)
     */
    @Bean
    public NewTopic bookingCancelledTopic() {
        return TopicBuilder.name("holiday-planner.booking.cancelled")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic paymentRefundedTopic() {
        return TopicBuilder.name("holiday-planner.payment.refunded")
                .partitions(3)
                .replicas(1)
                .build();
    }

    // ============================================================
    // KAFKA PRODUCER - Publishing Events
    // ============================================================

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 10);
        configProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // ============================================================
    // KAFKA CONSUMER - Listening to Events
    // ============================================================

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, "identity-service");
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, "com.holidayplanner.identityservice.event.DomainEvent");
        configProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        configProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
        return new DefaultKafkaConsumerFactory<>(configProps);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(3);
        return factory;
    }
}
