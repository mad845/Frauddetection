package com.frauddetect.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class KafkaProducerService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.kafka.topic}")
    private String topic;

    public KafkaProducerService(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishTransaction(Map<String, Object> transactionPayload) {
        try {
            String json = objectMapper.writeValueAsString(transactionPayload);
            kafkaTemplate.send(topic, (String) transactionPayload.get("transactionUuid"), json);
        } catch (Exception e) {
            throw new RuntimeException("Error serializando transaccion para Kafka", e);
        }
    }
}
