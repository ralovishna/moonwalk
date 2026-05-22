package com.midaas.moonwalk.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.midaas.moonwalk.service.KitchenDispatcherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KitchenEventListener {

    private final KitchenDispatcherService kitchenDispatcher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @KafkaListener(topics = "moonwalk.kitchen.events", groupId = "kitchen-service-group")
    public void handleDishQueued(String messagePayload) {
        try {
            var payload = objectMapper.readTree(messagePayload);

            if (payload.has("assigned") || payload.has("isReady")) {
                return;
            }

            log.info("Kitchen Consumer received new dish: {}", messagePayload);

            var orderItemId = payload.get("orderItemId").asLong();
            var restaurantId = payload.get("restaurantId").asLong();
            var prepTime = payload.get("prepTime").asInt();

            kitchenDispatcher.processDishQueuedEvent(restaurantId, orderItemId, prepTime);

        } catch (Exception e) {
            log.error("Error processing dish in Kitchen. Kafka will retry if configured.", e);
        }
    }
}