package com.enterprise.testing.functional.kafka;

import com.enterprise.testing.shared.config.FrameworkConfig;
import com.enterprise.testing.shared.model.KafkaEvent;
import com.enterprise.testing.shared.util.JsonUtil;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * Test Kafka consumer that captures messages for assertion.
 * 
 * USAGE IN STEP DEFINITIONS:
 *   kafkaConsumer.subscribe("order.events");
 *   // ... trigger the action that produces events ...
 *   KafkaEvent event = kafkaConsumer.awaitEvent(
 *       e -> e.getEventType().equals("ORDER_CREATED"),
 *       Duration.ofSeconds(10)
 *   );
 *   assertThat(event.getPayload().get("orderId")).isEqualTo(expectedOrderId);
 * 
 * KEY DESIGN DECISIONS:
 * - Uses CopyOnWriteArrayList for thread safety (poll loop vs assertions)
 * - Auto-commits disabled; we want to see ALL messages from test start
 * - Captures raw records AND deserialized KafkaEvents
 */
public class KafkaTestConsumer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(KafkaTestConsumer.class);

    private final KafkaConsumer<String, String> consumer;
    private final List<ConsumerRecord<String, String>> rawRecords = new CopyOnWriteArrayList<>();
    private final List<KafkaEvent> capturedEvents = new CopyOnWriteArrayList<>();
    private volatile boolean polling = false;
    private Thread pollThread;

    public KafkaTestConsumer(String bootstrapServers) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        this.consumer = new KafkaConsumer<>(props);
    }

    public KafkaTestConsumer() {
        this(FrameworkConfig.getInstance().getKafkaBootstrapServers());
    }

    /**
     * Subscribe to topics and start background polling.
     */
    public void subscribe(String... topics) {
        consumer.subscribe(Arrays.asList(topics));
        startPolling();
        log.info("Subscribed to topics: {}", Arrays.toString(topics));
    }

    /**
     * Wait for a specific event matching a predicate.
     * This is the primary assertion method used in step definitions.
     */
    public KafkaEvent awaitEvent(Predicate<KafkaEvent> matcher, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();

        while (System.currentTimeMillis() < deadline) {
            // Check already-captured events
            Optional<KafkaEvent> found = capturedEvents.stream().filter(matcher).findFirst();
            if (found.isPresent()) {
                log.info("Found matching event: {}", found.get());
                return found.get();
            }

            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while awaiting Kafka event", e);
            }
        }

        throw new AssertionError("Timed out waiting for Kafka event after " + timeout.getSeconds() +
                "s. Captured " + capturedEvents.size() + " events: " + capturedEvents);
    }

    /**
     * Get all captured events for a specific event type.
     */
    public List<KafkaEvent> getEventsByType(String eventType) {
        return capturedEvents.stream()
                .filter(e -> eventType.equals(e.getEventType()))
                .toList();
    }

    /**
     * Get all captured events on a specific topic.
     */
    public List<KafkaEvent> getEventsByTopic(String topic) {
        return capturedEvents.stream()
                .filter(e -> topic.equals(e.getTopic()))
                .toList();
    }

    /**
     * Get all captured events.
     */
    public List<KafkaEvent> getAllEvents() {
        return List.copyOf(capturedEvents);
    }

    /**
     * Get raw consumer records (for contract testing / schema validation).
     */
    public List<ConsumerRecord<String, String>> getRawRecords() {
        return List.copyOf(rawRecords);
    }

    /**
     * Clear all captured events. Call between scenarios.
     */
    public void reset() {
        rawRecords.clear();
        capturedEvents.clear();
        log.debug("Kafka consumer reset");
    }

    private void startPolling() {
        if (polling) return;
        polling = true;

        pollThread = new Thread(() -> {
            while (polling) {
                try {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                    for (ConsumerRecord<String, String> record : records) {
                        rawRecords.add(record);
                        try {
                            KafkaEvent event = JsonUtil.fromJson(record.value(), KafkaEvent.class);
                            if (event.getTopic() == null) {
                                event.setTopic(record.topic());
                            }
                            capturedEvents.add(event);
                            log.debug("Captured event: {} on topic {}", event.getEventType(), record.topic());
                        } catch (Exception e) {
                            log.warn("Failed to deserialize Kafka message on topic {}: {}", record.topic(), e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    if (polling) {
                        log.error("Error polling Kafka", e);
                    }
                }
            }
        }, "kafka-test-consumer-poll");
        pollThread.setDaemon(true);
        pollThread.start();
    }

    @Override
    public void close() {
        polling = false;
        if (pollThread != null) {
            pollThread.interrupt();
        }
        consumer.close();
        log.info("Kafka test consumer closed");
    }
}
