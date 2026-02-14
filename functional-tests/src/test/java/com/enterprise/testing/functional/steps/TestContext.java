package com.enterprise.testing.functional.steps;

import com.enterprise.testing.datagen.TestDataRegistry;
import com.enterprise.testing.functional.db.DatabaseHelper;
import com.enterprise.testing.functional.kafka.KafkaTestConsumer;
import com.enterprise.testing.functional.kafka.KafkaTestProducer;
import com.enterprise.testing.shared.model.SyntheticDataSet;
import io.restassured.response.Response;

import java.util.HashMap;
import java.util.Map;

/**
 * Shared test context passed between Cucumber step definitions.
 * 
 * WHY: Cucumber creates step definition classes per scenario, but
 * steps often span multiple classes (API steps, Kafka steps, DB steps).
 * This context class is the bridge - it holds shared state.
 */
public class TestContext {

    private SyntheticDataSet currentDataSet;
    private TestDataRegistry dataRegistry;
    private Response lastApiResponse;
    private String lastRequestBody;
    private KafkaTestConsumer kafkaConsumer;
    private KafkaTestProducer kafkaProducer;
    private DatabaseHelper dbHelper;
    private final Map<String, Object> scenarioData = new HashMap<>();

    public TestDataRegistry getDataRegistry() {
        if (dataRegistry == null) {
            dataRegistry = TestDataRegistry.getInstance();
        }
        return dataRegistry;
    }

    public void setDataRegistry(TestDataRegistry dataRegistry) {
        this.dataRegistry = dataRegistry;
    }

    public SyntheticDataSet getCurrentDataSet() {
        return currentDataSet;
    }

    public void setCurrentDataSet(SyntheticDataSet currentDataSet) {
        this.currentDataSet = currentDataSet;
    }

    public Response getLastApiResponse() {
        return lastApiResponse;
    }

    public void setLastApiResponse(Response lastApiResponse) {
        this.lastApiResponse = lastApiResponse;
    }

    public String getLastRequestBody() {
        return lastRequestBody;
    }

    public void setLastRequestBody(String lastRequestBody) {
        this.lastRequestBody = lastRequestBody;
    }

    public KafkaTestConsumer getKafkaConsumer() {
        return kafkaConsumer;
    }

    public void setKafkaConsumer(KafkaTestConsumer kafkaConsumer) {
        this.kafkaConsumer = kafkaConsumer;
    }

    public KafkaTestProducer getKafkaProducer() {
        return kafkaProducer;
    }

    public void setKafkaProducer(KafkaTestProducer kafkaProducer) {
        this.kafkaProducer = kafkaProducer;
    }

    public DatabaseHelper getDbHelper() {
        return dbHelper;
    }

    public void setDbHelper(DatabaseHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    public void put(String key, Object value) {
        scenarioData.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        return (T) scenarioData.get(key);
    }

    public Object get(String key) {
        return scenarioData.get(key);
    }

    /**
     * Clean up resources after each scenario.
     */
    public void cleanup() {
        if (kafkaConsumer != null) {
            kafkaConsumer.reset();
        }
        scenarioData.clear();
        lastApiResponse = null;
        lastRequestBody = null;
        currentDataSet = null;
    }
}
