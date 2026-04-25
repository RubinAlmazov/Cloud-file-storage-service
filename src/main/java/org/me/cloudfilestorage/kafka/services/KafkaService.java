package org.me.cloudfilestorage.kafka.services;

import lombok.RequiredArgsConstructor;
import org.me.cloudfilestorage.minio.services.ResourceService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KafkaService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ResourceService resourceService;

    public void sendToKafka(Integer userId) {
        kafkaTemplate.send("userId", userId.toString());
    }

    @KafkaListener(topics = "userId")
    public void consumeUserId(String id) {
        resourceService.createFolderForUser(id);
    }
}
