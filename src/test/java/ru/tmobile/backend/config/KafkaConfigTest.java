package ru.tmobile.backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import ru.tmobile.backend.dto.ConsumptionEventDto;
import ru.tmobile.backend.virtual_clock.TimeSyncConsumer.TimeChangeEvent;

class KafkaConfigTest {
    KafkaConfig config;

    @BeforeEach void up() {
        config = new KafkaConfig();
        ReflectionTestUtils.setField(config, "bootstrapServers", "kafka:9092");
        ReflectionTestUtils.setField(config, "groupId", "tmobile-group");
    }

    @Test void timeChangeConsumerFactoryUsesInjectedServersAndGroup() {
        ConsumerFactory<String, TimeChangeEvent> cf = config.timeChangeConsumerFactory();
        assertNotNull(cf);
        Object servers = cf.getConfigurationProperties().get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG);
        Object group = cf.getConfigurationProperties().get(ConsumerConfig.GROUP_ID_CONFIG);
        Object reset = cf.getConfigurationProperties().get(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG);
        assertEquals("kafka:9092", servers);
        assertEquals("tmobile-group", group);
        assertEquals("earliest", reset);
    }

    @Test void stringConsumerFactoryUsesInjectedServersAndGroup() {
        ConsumerFactory<String, String> cf = config.stringConsumerFactory();
        assertNotNull(cf);
        assertEquals("kafka:9092", cf.getConfigurationProperties().get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG));
        assertEquals("tmobile-group", cf.getConfigurationProperties().get(ConsumerConfig.GROUP_ID_CONFIG));
    }

    @Test void consumptionEventConsumerFactoryUsesInjectedServersAndGroup() {
        ConsumerFactory<String, ConsumptionEventDto> cf = config.consumptionEventConsumerFactory();
        assertNotNull(cf);
        assertEquals("kafka:9092", cf.getConfigurationProperties().get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG));
    }

    @Test void kafkaListenerContainerFactoryUsesTimeChangeConsumerFactory() {
        ConsumerFactory<String, TimeChangeEvent> cf = config.timeChangeConsumerFactory();
        ConcurrentKafkaListenerContainerFactory<String, TimeChangeEvent> f = config.kafkaListenerContainerFactory(cf);
        assertSame(cf, f.getConsumerFactory());
    }

    @Test void stringKafkaListenerContainerFactoryUsesStringConsumerFactory() {
        ConsumerFactory<String, String> cf = config.stringConsumerFactory();
        ConcurrentKafkaListenerContainerFactory<String, String> f = config.stringKafkaListenerContainerFactory(cf);
        assertSame(cf, f.getConsumerFactory());
    }

    @Test void consumptionEventListenerContainerFactoryCanBeBuilt() {
        ConsumerFactory<String, ConsumptionEventDto> cf = config.consumptionEventConsumerFactory();
        ConcurrentKafkaListenerContainerFactory<String, ConsumptionEventDto> f =
            config.consumptionEventKafkaListenerContainerFactory(cf);
        assertSame(cf, f.getConsumerFactory());
    }
}