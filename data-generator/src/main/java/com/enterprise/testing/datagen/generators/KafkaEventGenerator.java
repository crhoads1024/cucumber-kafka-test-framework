package com.enterprise.testing.datagen.generators;

import com.enterprise.testing.shared.model.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates the expected Kafka events that SHOULD be produced when
 * an order is processed. These become the assertion baseline in
 * functional and contract tests.
 * 
 * KEY CONCEPT: We generate expected events at data creation time.
 * This means functional tests can assert: "When I submit this order,
 * the Kafka events produced should match these expected events."
 */
public class KafkaEventGenerator {

    private static final String ORDER_TOPIC = "order.events";
    private static final String NOTIFICATION_TOPIC = "notification.events";
    private static final String SOURCE = "order-service";

    /**
     * Generate all expected events for a given order.
     * An order going through the happy path produces:
     * 1. ORDER_CREATED on order.events
     * 2. ORDER_CONFIRMED on order.events (if status >= CONFIRMED)
     * 3. NOTIFICATION_SENT on notification.events
     */
    public static List<KafkaEvent> fromOrder(Order order) {
        List<KafkaEvent> events = new ArrayList<>();

        // Every order produces a creation event
        events.add(createOrderEvent("ORDER_CREATED", order));

        // If the order is confirmed or beyond, produce confirmation event
        if (isConfirmedOrBeyond(order.getStatus())) {
            events.add(createOrderEvent("ORDER_CONFIRMED", order));
        }

        // Notification event for confirmed orders
        if (isConfirmedOrBeyond(order.getStatus())) {
            events.add(createNotificationEvent(order));
        }

        return events;
    }

    /**
     * Generate expected events for all orders in a data set.
     */
    public static List<KafkaEvent> fromOrders(List<Order> orders) {
        List<KafkaEvent> events = new ArrayList<>();
        for (Order order : orders) {
            events.addAll(fromOrder(order));
        }
        return events;
    }

    private static KafkaEvent createOrderEvent(String eventType, Order order) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getId());
        payload.put("customerId", order.getCustomerId());
        payload.put("totalAmount", order.getTotalAmount());
        payload.put("itemCount", order.getItems().size());
        payload.put("status", order.getStatus().name());

        return new KafkaEvent(eventType, ORDER_TOPIC, SOURCE, payload)
                .withCorrelationId(order.getId());
    }

    private static KafkaEvent createNotificationEvent(Order order) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getId());
        payload.put("customerId", order.getCustomerId());
        payload.put("notificationType", "ORDER_CONFIRMATION");
        payload.put("channel", "EMAIL");

        return new KafkaEvent("NOTIFICATION_SENT", NOTIFICATION_TOPIC, SOURCE, payload)
                .withCorrelationId(order.getId());
    }

    private static boolean isConfirmedOrBeyond(OrderStatus status) {
        return status == OrderStatus.CONFIRMED ||
                status == OrderStatus.PROCESSING ||
                status == OrderStatus.SHIPPED ||
                status == OrderStatus.DELIVERED;
    }
}
