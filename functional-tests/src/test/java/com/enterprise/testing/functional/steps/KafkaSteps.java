package com.enterprise.testing.functional.steps;

import com.enterprise.testing.functional.kafka.KafkaTestConsumer;
import com.enterprise.testing.shared.model.KafkaEvent;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Then;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for Kafka event assertions.
 * 
 * HOW TO WRITE KAFKA TEST STEPS:
 * 
 * 1. Events are captured by KafkaTestConsumer in a background thread
 * 2. Use awaitEvent() with a timeout for async assertions
 * 3. Match events by type, topic, or payload fields
 * 4. Store matched events in context for subsequent assertions
 * 
 * COMMON PATTERNS:
 * - "within X seconds a Kafka event should appear on <topic>"
 *    -> awaitEvent with timeout
 * - "the event payload should contain field <field>"
 *    -> inspect the last matched event
 * - "no Kafka event should appear on <topic>"
 *    -> negative assertion with short timeout
 */
public class KafkaSteps {

    private static final Logger log = LoggerFactory.getLogger(KafkaSteps.class);
    private final TestContext context;

    public KafkaSteps() {
        this.context = SharedTestContext.get();
    }

    @Then("within {int} seconds a Kafka event should appear on {string} with:")
    public void awaitKafkaEvent(int timeoutSeconds, String topic, DataTable dataTable) {
        KafkaTestConsumer consumer = context.getKafkaConsumer();
        assertThat(consumer).as("Kafka consumer not initialized. Add Background step.").isNotNull();

        Map<String, String> expectedFields = dataTable.asMaps(String.class, String.class).get(0);

        // Determine the current scenario's correlation ID for scoping
        String scenarioCorrelationId = resolveCurrentCorrelationId();

        KafkaEvent event = consumer.awaitEvent(e -> {
            if (!topic.equals(e.getTopic())) return false;

            // Scope to current scenario's correlation ID if available
            if (scenarioCorrelationId != null && e.getCorrelationId() != null) {
                if (!scenarioCorrelationId.equals(e.getCorrelationId())) return false;
            }

            for (Map.Entry<String, String> entry : expectedFields.entrySet()) {
                String field = entry.getKey();
                String expectedValue = entry.getValue();

                String actualValue = getEventField(e, field);
                if (!expectedValue.equals(actualValue)) return false;
            }
            return true;
        }, Duration.ofSeconds(timeoutSeconds));

        // Store the matched event for subsequent payload assertions
        context.put("lastKafkaEvent", event);
        log.info("Matched Kafka event: {} on {} (correlationId={})",
                event.getEventType(), topic, event.getCorrelationId());
    }

    @Then("the event payload should contain field {string}")
    public void eventPayloadContainsField(String fieldName) {
        KafkaEvent event = (KafkaEvent) context.get("lastKafkaEvent");
        assertThat(event).as("No Kafka event captured").isNotNull();
        assertThat(event.getPayload())
                .as("Event payload should contain field '%s'", fieldName)
                .containsKey(fieldName);
    }

    @Then("the event payload should contain field {string} matching the generated user ID")
    public void eventPayloadMatchesUserId(String fieldName) {
        KafkaEvent event = (KafkaEvent) context.get("lastKafkaEvent");
        assertThat(event).as("No Kafka event captured").isNotNull();

        String expectedUserId = context.getCurrentDataSet().getUser().getId();
        String actualValue = String.valueOf(event.getPayload().get(fieldName));

        assertThat(actualValue)
                .as("Event field '%s' should match generated user ID", fieldName)
                .isEqualTo(expectedUserId);
    }

    @Then("the Kafka events on {string} should appear in order:")
    public void eventsInOrder(String topic, List<String> expectedOrder) {
        KafkaTestConsumer consumer = context.getKafkaConsumer();
        String scenarioCorrelationId = resolveCurrentCorrelationId();

        // Wait a bit for all events to arrive
        try { Thread.sleep(3000); } catch (InterruptedException ignored) {}

        List<KafkaEvent> topicEvents = consumer.getEventsByTopic(topic);

        // Scope to current scenario's correlation ID
        if (scenarioCorrelationId != null) {
            topicEvents = topicEvents.stream()
                    .filter(e -> scenarioCorrelationId.equals(e.getCorrelationId()))
                    .toList();
        }

        List<String> actualOrder = topicEvents.stream()
                .map(KafkaEvent::getEventType)
                .toList();

        assertThat(actualOrder)
                .as("Events on '%s' should appear in order %s (correlationId=%s)", topic, expectedOrder, scenarioCorrelationId)
                .containsSubsequence(expectedOrder.toArray(new String[0]));
    }

    @Then("no Kafka event should appear on {string} within {int} seconds")
    public void noEventOnTopic(String topic, int timeoutSeconds) {
        KafkaTestConsumer consumer = context.getKafkaConsumer();
        String scenarioCorrelationId = resolveCurrentCorrelationId();

        try {
            Thread.sleep(timeoutSeconds * 1000L);
        } catch (InterruptedException ignored) {}

        List<KafkaEvent> events = consumer.getEventsByTopic(topic);

        // Scope to current scenario's correlation ID
        if (scenarioCorrelationId != null) {
            events = events.stream()
                    .filter(e -> scenarioCorrelationId.equals(e.getCorrelationId()))
                    .toList();
        }

        assertThat(events)
                .as("Expected no events on topic '%s' for correlationId '%s' but found %d",
                        topic, scenarioCorrelationId, events.size())
                .isEmpty();
    }

    // ===== HELPERS =====

    /**
     * Resolve the current scenario's correlation ID for scoping Kafka matches.
     * This prevents matching stale events from previous test runs.
     * Order scenarios use created.order.id, trade scenarios use created.trade.id.
     */
    private String resolveCurrentCorrelationId() {
        Object orderId = context.get("created.order.id");
        if (orderId != null) return orderId.toString();

        Object tradeId = context.get("created.trade.id");
        if (tradeId != null) return tradeId.toString();

        return null;
    }

    /**
     * Get a field value from a KafkaEvent, supporting both top-level
     * event fields and payload fields.
     */
    private String getEventField(KafkaEvent event, String field) {
        return switch (field) {
            case "eventType" -> event.getEventType();
            case "topic" -> event.getTopic();
            case "source" -> event.getSource();
            case "correlationId" -> event.getCorrelationId();
            default -> {
                // Check payload
                Object value = event.getPayload() != null ? event.getPayload().get(field) : null;
                yield value != null ? value.toString() : null;
            }
        };
    }
}
