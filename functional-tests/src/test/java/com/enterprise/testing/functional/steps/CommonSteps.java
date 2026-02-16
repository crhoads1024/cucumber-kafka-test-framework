package com.enterprise.testing.functional.steps;

import com.enterprise.testing.datagen.TestDataRegistry;
import com.enterprise.testing.functional.db.DatabaseHelper;
import com.enterprise.testing.functional.kafka.KafkaTestConsumer;
import com.enterprise.testing.functional.kafka.KafkaTestProducer;
import com.enterprise.testing.shared.config.FrameworkConfig;
import com.enterprise.testing.shared.model.SyntheticDataSet;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Common step definitions shared across all feature files.
 * Handles test data loading, Kafka setup, DB setup, and cleanup.
 * 
 * HOW TO ADD NEW COMMON STEPS:
 * 1. Add the @Given/@When/@Then annotated method here
 * 2. Use TestContext to share state with other step def classes
 * 3. Keep steps reusable - avoid scenario-specific logic
 */
public class CommonSteps {

    private static final Logger log = LoggerFactory.getLogger(CommonSteps.class);
    private final TestContext context;

    // Cucumber instantiates step def classes per scenario.
    // Share state via TestContext.
    public CommonSteps() {
        this.context = SharedTestContext.get();
    }

    // ===== LIFECYCLE HOOKS =====

    @Before
    public void setUp() {
        log.info("--- Scenario starting ---");
    }

    @After
    public void tearDown() {
        // Clean DB test data between scenarios to ensure isolation
        if (context.getDbHelper() != null) {
            try {
                new com.enterprise.testing.functional.db.DatabaseSeeder(context.getDbHelper()).cleanAll();
            } catch (Exception e) {
                log.warn("DB cleanup failed (non-fatal): {}", e.getMessage());
            }
        }
        context.cleanup();
        log.info("--- Scenario complete ---");
    }

    // ===== DATA LOADING STEPS =====

    @Given("synthetic test data is loaded for scenario {string}")
    public void loadSyntheticData(String scenarioId) {
        TestDataRegistry registry = context.getDataRegistry();

        // Generate if not already available (idempotent)
        if (!registry.hasScenario(scenarioId)) {
            log.info("Generating data for scenario: {}", scenarioId);
            registry.generateScenario(scenarioId, 1, 2);
        }

        SyntheticDataSet dataSet = registry.getScenario(scenarioId);
        context.setCurrentDataSet(dataSet);

        log.info("Loaded scenario '{}': user={}, orders={}, expectedEvents={}",
                scenarioId,
                dataSet.getUser().getEmail(),
                dataSet.getOrders().size(),
                dataSet.getExpectedEvents().size());
    }

    // ===== KAFKA SETUP STEPS =====

    @Given("Kafka consumers are listening on topics:")
    public void kafkaConsumersListening(List<String> topics) {
        FrameworkConfig config = FrameworkConfig.getInstance();
        KafkaTestConsumer consumer = new KafkaTestConsumer(config.getKafkaBootstrapServers());
        consumer.subscribe(topics.toArray(new String[0]));
        context.setKafkaConsumer(consumer);

        KafkaTestProducer producer = new KafkaTestProducer(config.getKafkaBootstrapServers());
        context.setKafkaProducer(producer);

        log.info("Kafka consumers listening on: {}", topics);
    }

    // ===== DATABASE SETUP STEPS =====

    @Given("a database connection is established")
    public void databaseConnectionEstablished() {
        if (context.getDbHelper() == null) {
            DatabaseHelper dbHelper = new DatabaseHelper();
            context.setDbHelper(dbHelper);
            log.info("Database connection established");
        }
    }
}
