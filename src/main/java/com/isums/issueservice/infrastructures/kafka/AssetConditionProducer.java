package com.isums.issueservice.infrastructures.kafka;

import com.isums.issueservice.domains.events.AssetConditionEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AssetConditionProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendConditionUpdate(AssetConditionEvent event){
        kafkaTemplate.send("asset-condition-update-topic", event);

    }
}
