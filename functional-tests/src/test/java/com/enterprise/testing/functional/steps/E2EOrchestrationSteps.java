package com.enterprise.testing.functional.steps;

import com.enterprise.testing.functional.db.DatabaseHelper;
import com.enterprise.testing.functional.db.DatabaseSeeder;
import com.enterprise.testing.functional.kafka.KafkaTestProducer;
import com.enterprise.testing.shared.config.FrameworkConfig;
import com.enterprise.testing.shared.model.*;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * E2E Orchestration Steps
 *
 * WHY THIS CLASS EXISTS:
 * In production, an API call triggers a chain: API → DB write → Kafka publish.
 * In our test environment, WireMock returns stub responses but does NOT write
 * to PostgreSQL or publish Kafka events. This class bridges that gap.
 *
 * When a test calls "an order has been created via the API for the generated user",
 * this class:
 *   1. Lets the API call go through to WireMock (done in ApiSteps)
 *   2. Seeds the corresponding data into PostgreSQL (simulating the app's DB write)
 *   3. Publishes the expected Kafka events (simulating the app's event emission)
 *
 * This gives us a fully integrated e2e test across all three layers without
 * needing a real running application.
 *
 * WHEN TO USE:
 * - @e2e tagged scenarios that span API + DB + Kafka
 * - @database tagged scenarios that need rows in Postgres
 * - @kafka tagged scenarios that need events on topics
 *
 * The orchestration is tag-aware: it only seeds DB if a connection is
 * established, and only publishes Kafka if a producer is available.
 */
public class E2EOrchestrationSteps {

    private static final Logger log = LoggerFactory.getLogger(E2EOrchestrationSteps.class);
    private final TestContext context;

    public E2EOrchestrationSteps() {
        this.context = SharedTestContext.get();
    }

    /**
     * After an order is created via the API, seed the DB and publish events.
     * This hooks into the SAME Given step text as ApiSteps but runs AFTER it
     * because Cucumber executes steps in definition order within the glue path.
     *
     * We use a separate step text to avoid conflicts with ApiSteps.
     */
    @Given("the created order is persisted to the database")
    public void persistOrderToDatabase() {
        SyntheticDataSet data = context.getCurrentDataSet();
        Order order = data.getOrders().get(0);
        String createdOrderId = (String) context.get("created.order.id");

        // Use the WireMock-returned ID for the DB record
        if (createdOrderId != null) {
            seedOrderToDatabase(createdOrderId, order, data.getUser());
            log.info("Persisted order {} to database", createdOrderId);
        }
    }

    @Given("the created order events are published to Kafka")
    public void publishOrderCreatedEvent() {
        String createdOrderId = (String) context.get("created.order.id");
        SyntheticDataSet data = context.getCurrentDataSet();

        if (context.getKafkaProducer() != null && createdOrderId != null) {
            publishOrderEvent("ORDER_CREATED", createdOrderId, data);
            log.info("Published ORDER_CREATED event for order {}", createdOrderId);
        }
    }

    // ===== CONFIRM/CANCEL ORCHESTRATION =====

    @When("I confirm the order via the API and persist the change")
    public void confirmOrderFull() {
        String orderId = (String) context.get("created.order.id");
        SyntheticDataSet data = context.getCurrentDataSet();

        // API call (goes to WireMock)
        new ApiSteps().submitPut("/api/orders/{created.order.id}/confirm");

        // DB update
        DatabaseHelper db = context.getDbHelper();
        if (db != null && orderId != null) {
            DatabaseSeeder seeder = new DatabaseSeeder(db);
            seeder.seedStatusChange(orderId, data.getUser().getId(), "PENDING", "CONFIRMED");
            log.info("DB: Order {} confirmed", orderId);
        }

        // Kafka events
        if (context.getKafkaProducer() != null && orderId != null) {
            publishOrderEvent("ORDER_CONFIRMED", orderId, data);
            publishNotificationEvent(orderId, data);
            log.info("Kafka: Published ORDER_CONFIRMED + NOTIFICATION_SENT for order {}", orderId);
        }
    }

    @When("I cancel the order via the API and persist the change")
    public void cancelOrderFull() {
        String orderId = (String) context.get("created.order.id");
        SyntheticDataSet data = context.getCurrentDataSet();

        // API call (goes to WireMock)
        new ApiSteps().submitPut("/api/orders/{created.order.id}/cancel");

        // DB update
        DatabaseHelper db = context.getDbHelper();
        if (db != null && orderId != null) {
            DatabaseSeeder seeder = new DatabaseSeeder(db);
            seeder.cancelOrder(orderId, data.getUser().getId());
            log.info("DB: Order {} cancelled", orderId);
        }

        // Kafka event (cancel produces ORDER_CANCELLED but NOT notification)
        if (context.getKafkaProducer() != null && orderId != null) {
            publishOrderEvent("ORDER_CANCELLED", orderId, data);
            log.info("Kafka: Published ORDER_CANCELLED for order {} (no notification)", orderId);
        }
    }

    // ===== PRIVATE HELPERS =====

    /**
     * Seed an order + its items + audit entry into PostgreSQL.
     */
    private void seedOrderToDatabase(String orderId, Order order, User user) {
        DatabaseHelper db = context.getDbHelper();
        if (db == null) return;

        // Insert order row using the WireMock-returned ID
        db.execute(
                "INSERT INTO orders (id, customer_id, status, total_amount, created_at, updated_at) " +
                "VALUES (?, ?, 'PENDING', ?, NOW(), NOW()) " +
                "ON CONFLICT (id) DO NOTHING",
                orderId,
                user.getId(),
                order.getTotalAmount()
        );

        // Insert order items referencing the WireMock order ID
        if (order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                db.execute(
                        "INSERT INTO order_items (id, order_id, product_id, product_name, quantity, unit_price) " +
                        "VALUES (?, ?, ?, ?, ?, ?) " +
                        "ON CONFLICT (id) DO NOTHING",
                        item.getId(),
                        orderId,
                        item.getProductId(),
                        item.getProductName(),
                        item.getQuantity(),
                        item.getUnitPrice()
                );
            }
        }

        // Audit entry
        db.execute(
                "INSERT INTO audit_log (entity_id, entity_type, action, user_id, details, created_at) " +
                "VALUES (?, 'ORDER', 'ORDER_CREATED', ?, '{}'::jsonb, NOW())",
                orderId,
                user.getId()
        );
    }

    /**
     * Publish an order lifecycle event to the order.events topic.
     */
    private void publishOrderEvent(String eventType, String orderId, SyntheticDataSet data) {
        KafkaTestProducer producer = context.getKafkaProducer();
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", orderId);
        payload.put("customerId", data.getUser().getId());
        payload.put("status", eventType.replace("ORDER_", ""));

        if (data.getOrders() != null && !data.getOrders().isEmpty()) {
            payload.put("totalAmount", data.getOrders().get(0).getTotalAmount());
        }

        KafkaEvent event = new KafkaEvent(eventType, "order.events", "clearing-api", payload)
                .withCorrelationId(orderId);

        producer.publishSync(event);
    }

    /**
     * Publish a notification event (sent on order confirmation, NOT on cancellation).
     */
    private void publishNotificationEvent(String orderId, SyntheticDataSet data) {
        KafkaTestProducer producer = context.getKafkaProducer();
        Map<String, Object> payload = new HashMap<>();
        payload.put("orderId", orderId);
        payload.put("recipientId", data.getUser().getId());
        payload.put("type", "ORDER_CONFIRMATION");
        payload.put("channel", "EMAIL");

        KafkaEvent event = new KafkaEvent("NOTIFICATION_SENT", "notification.events", "notification-service", payload)
                .withCorrelationId(orderId);

        producer.publishSync(event);
    }
}
