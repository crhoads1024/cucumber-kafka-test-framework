package com.enterprise.testing.functional.kafka;

import com.enterprise.testing.shared.config.FrameworkConfig;
import com.enterprise.testing.shared.model.KafkaEvent;
import com.enterprise.testing.shared.util.JsonUtil;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Test Kafka producer for publishing events during test execution.
 * Use this when you need to simulate upstream events that trigger
 * processing in the system under test.
 */
public class KafkaTestProducer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(KafkaTestProducer.class);
    private final KafkaProducer<String, String> producer;

    public KafkaTestProducer(String bootstrapServers) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");

        this.producer = new KafkaProducer<>(props);
    }

    public KafkaTestProducer() {
        this(FrameworkConfig.getInstance().getKafkaBootstrapServers());
    }

    /**
     * Publish a KafkaEvent and wait for acknowledgement.
     */
    public RecordMetadata publishSync(KafkaEvent event) {
        String json = JsonUtil.toJson(event);
        ProducerRecord<String, String> record = new ProducerRecord<>(
                event.getTopic(),
                event.getCorrelationId(),  // Use correlationId as key for partitioning
                json
        );

        try {
            Future<RecordMetadata> future = producer.send(record);
            RecordMetadata metadata = future.get(10, TimeUnit.SECONDS);
            log.info("Published event {} to {}[partition={}, offset={}]",
                    event.getEventType(), event.getTopic(),
                    metadata.partition(), metadata.offset());
            return metadata;
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish Kafka event: " + event, e);
        }
    }

    /**
     * Publish a raw JSON message to a topic.
     */
    public RecordMetadata publishRaw(String topic, String key, String value) {
        try {
            Future<RecordMetadata> future = producer.send(new ProducerRecord<>(topic, key, value));
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish raw message to " + topic, e);
        }
    }

    @Override
    public void close() {
        producer.close();
        log.info("Kafka test producer closed");
    }
}
