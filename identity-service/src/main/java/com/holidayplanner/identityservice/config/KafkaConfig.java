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
    //CHECK: Are these all the required topics? There are more endpoints than this, but maybe they dont need the even/broadcast, just the return value?
    //Also means we have only these topics in a bunch of other files and in the payloads in the shared module. 
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

}
