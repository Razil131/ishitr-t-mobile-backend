package ru.tmobile.backend;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class ConsumptionEventListener {

    @KafkaListener(topics = "consumption-events", groupId = "tmobile-group")
    public void listenConsumptionEvents(String message) {
        System.out.println("[Kafka Consumer]: " + message);
    }
}