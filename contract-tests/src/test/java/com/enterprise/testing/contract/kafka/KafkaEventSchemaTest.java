package com.enterprise.testing.contract.kafka;

import com.enterprise.testing.shared.model.KafkaEvent;
import com.enterprise.testing.shared.model.Order;
import com.enterprise.testing.shared.model.OrderItem;
import com.enterprise.testing.shared.model.OrderStatus;
import com.enterprise.testing.shared.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kafka Event Schema Contract Tests.
 * 
 * HOW TO WRITE KAFKA CONTRACT TESTS:
 * 
 * 1. Define JSON schemas for each event type (in resources/schemas/)
 * 2. Generate sample events using your generators
 * 3. Validate each event against its schema
 * 4. Test both VALID events (should pass) and INVALID events (should fail)
 * 
 * WHY SCHEMA TESTING MATTERS:
 * - Catches breaking changes in event structure
 * - Ensures producers and consumers agree on format
 * - Prevents deployment of incompatible services
 * - Acts as living documentation of your event contracts
 */
public class KafkaEventSchemaTest {

    private static JsonSchema orderEventSchema;

    @BeforeAll
    static void loadSchemas() {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
        InputStream schemaStream = KafkaEventSchemaTest.class
                .getResourceAsStream("/schemas/order-event.schema.json");
        orderEventSchema = factory.getSchema(schemaStream);
    }

    @Nested
    @DisplayName("Valid Order Events")
    class ValidEvents {

        @ParameterizedTest(name = "Event type {0} should conform to schema")
        @ValueSource(strings = {"ORDER_CREATED", "ORDER_CONFIRMED", "ORDER_CANCELLED", "ORDER_SHIPPED"})
        void validEventTypesConformToSchema(String eventType) {
            KafkaEvent event = createSampleEvent(eventType);
            String json = JsonUtil.toJson(event);
            JsonNode node = JsonUtil.getMapper().valueToTree(event);

            Set<ValidationMessage> errors = orderEventSchema.validate(node);

            assertThat(errors)
                    .as("Event type '%s' should conform to schema. Violations: %s", eventType, errors)
                    .isEmpty();
        }

        @Test
        @DisplayName("Generated event from OrderGenerator conforms to schema")
        void generatedEventConformsToSchema() {
            // Simulate what the actual system would produce
            Order order = createSampleOrder();

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("orderId", order.getId());
            payload.put("customerId", order.getCustomerId());
            payload.put("totalAmount", order.getTotalAmount());
            payload.put("itemCount", order.getItems().size());
            payload.put("status", order.getStatus().name());

            KafkaEvent event = new KafkaEvent("ORDER_CREATED", "order.events", "order-service", payload)
                    .withCorrelationId(order.getId());

            JsonNode node = JsonUtil.getMapper().valueToTree(event);
            Set<ValidationMessage> errors = orderEventSchema.validate(node);

            assertThat(errors).as("Generated event should conform to schema").isEmpty();
        }
    }

    @Nested
    @DisplayName("Invalid Order Events")
    class InvalidEvents {

        @Test
        @DisplayName("Event without required eventType should fail validation")
        void missingEventType() {
            String json = """
                    {
                        "eventId": "test-123",
                        "timestamp": "2024-01-01T00:00:00Z",
                        "source": "test",
                        "payload": {"orderId": "o1", "customerId": "c1"}
                    }
                    """;

            JsonNode node = JsonUtil.fromJson(json, JsonNode.class);
            Set<ValidationMessage> errors = orderEventSchema.validate(node);

            assertThat(errors)
                    .as("Missing eventType should produce validation errors")
                    .isNotEmpty();
        }

        @Test
        @DisplayName("Event with invalid eventType should fail validation")
        void invalidEventType() {
            KafkaEvent event = createSampleEvent("INVALID_TYPE");
            JsonNode node = JsonUtil.getMapper().valueToTree(event);

            Set<ValidationMessage> errors = orderEventSchema.validate(node);

            assertThat(errors)
                    .as("Invalid eventType should produce validation errors")
                    .isNotEmpty();
        }

        @Test
        @DisplayName("Event with missing payload fields should fail validation")
        void missingPayloadFields() {
            Map<String, Object> incompletePayload = new LinkedHashMap<>();
            // Missing required 'orderId' and 'customerId'
            incompletePayload.put("totalAmount", 99.99);

            KafkaEvent event = new KafkaEvent("ORDER_CREATED", "order.events", "order-service", incompletePayload);
            JsonNode node = JsonUtil.getMapper().valueToTree(event);

            Set<ValidationMessage> errors = orderEventSchema.validate(node);

            assertThat(errors)
                    .as("Missing payload fields should produce validation errors")
                    .isNotEmpty();
        }

        @Test
        @DisplayName("Event with extra top-level fields should fail validation")
        void extraFieldsRejected() {
            // additionalProperties: false means unknown fields are rejected
            String json = """
                    {
                        "eventId": "test-123",
                        "eventType": "ORDER_CREATED",
                        "timestamp": "2024-01-01T00:00:00Z",
                        "source": "test",
                        "unknownField": "surprise",
                        "payload": {"orderId": "o1", "customerId": "c1"}
                    }
                    """;

            JsonNode node = JsonUtil.fromJson(json, JsonNode.class);
            Set<ValidationMessage> errors = orderEventSchema.validate(node);

            assertThat(errors)
                    .as("Unknown top-level fields should be rejected")
                    .isNotEmpty();
        }
    }

    // ===== HELPERS =====

    private static KafkaEvent createSampleEvent(String eventType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", "order-" + UUID.randomUUID());
        payload.put("customerId", "cust-" + UUID.randomUUID());
        payload.put("totalAmount", 129.99);
        payload.put("itemCount", 3);
        payload.put("status", "CONFIRMED");

        return new KafkaEvent(eventType, "order.events", "order-service", payload)
                .withCorrelationId("order-123");
    }

    private static Order createSampleOrder() {
        Order order = new Order();
        order.setCustomerId("cust-test-123");
        order.setStatus(OrderStatus.CONFIRMED);
        order.addItem(new OrderItem("prod-001", "Widget", 2, new BigDecimal("29.99")));
        order.addItem(new OrderItem("prod-002", "Gadget", 1, new BigDecimal("49.99")));
        return order;
    }
}
