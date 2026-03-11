package org.me.cloudfilestorage.kafka.services;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KafkaProducerService {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public void sendToKafka(Integer userId) {
        kafkaTemplate.send("usersId", userId.toString());
    }
}
