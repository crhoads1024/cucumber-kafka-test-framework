package com.enterprise.testing.shared.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Standard event envelope for all Kafka messages in the system.
 * Every Kafka event follows this structure, which makes contract
 * testing and schema validation consistent.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class KafkaEvent {

    @JsonProperty("eventId")
    private String eventId;

    @JsonProperty("eventType")
    private String eventType;

    @JsonProperty("topic")
    private String topic;

    @JsonProperty("timestamp")
    private Instant timestamp;

    @JsonProperty("source")
    private String source;

    @JsonProperty("correlationId")
    private String correlationId;

    @JsonProperty("payload")
    private Map<String, Object> payload;

    public KafkaEvent() {
        this.eventId = UUID.randomUUID().toString();
        this.timestamp = Instant.now();
    }

    public KafkaEvent(String eventType, String topic, String source, Map<String, Object> payload) {
        this();
        this.eventType = eventType;
        this.topic = topic;
        this.source = source;
        this.payload = payload;
    }

    // --- Builder-style setters for fluent construction ---

    public KafkaEvent withCorrelationId(String correlationId) {
        this.correlationId = correlationId;
        return this;
    }

    // --- Getters and Setters ---

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload; }

    @Override
    public String toString() {
        return "KafkaEvent{type='" + eventType + "', topic='" + topic + "', correlationId='" + correlationId + "'}";
    }
}
