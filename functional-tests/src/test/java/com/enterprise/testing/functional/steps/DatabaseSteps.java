package com.enterprise.testing.functional.steps;

import com.enterprise.testing.functional.db.DatabaseHelper;
import com.enterprise.testing.shared.model.Order;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for database assertions.
 * 
 * HOW TO WRITE DATABASE TEST STEPS:
 * 
 * 1. Use DatabaseHelper for all SQL queries
 * 2. Assert on specific columns, not entire rows
 * 3. Use parameterized queries to prevent SQL injection (even in tests!)
 * 4. Handle {generated.xxx} placeholders for synthetic data values
 * 
 * COMMON PATTERNS:
 * - Query and assert row values with DataTable
 * - Count assertions for record existence
 * - Timestamp ordering assertions (updated_at > created_at)
 */
public class DatabaseSteps {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSteps.class);
    private final TestContext context;

    public DatabaseSteps() {
        this.context = SharedTestContext.get();
    }

    // ===== QUERY STEPS =====

    @When("I query the orders table for the created order ID")
    public void queryOrdersTable() {
        DatabaseHelper db = context.getDbHelper();
        String orderId = (String) context.get("created.order.id");

        Map<String, Object> row = db.queryRow(
                "SELECT * FROM orders WHERE id = ?", orderId
        );
        context.put("db.query.result", row);
        log.info("Queried order: {}", row);
    }

    @When("I query the order_items table for the created order ID")
    public void queryOrderItemsTable() {
        DatabaseHelper db = context.getDbHelper();
        String orderId = (String) context.get("created.order.id");

        List<Map<String, Object>> rows = db.queryList(
                "SELECT * FROM order_items WHERE order_id = ?", orderId
        );
        context.put("db.query.results", rows);
        log.info("Found {} order items", rows.size());
    }

    @When("I query the audit_log table for the created order ID")
    public void queryAuditLog() {
        DatabaseHelper db = context.getDbHelper();
        String orderId = (String) context.get("created.order.id");

        List<Map<String, Object>> rows = db.queryList(
                "SELECT * FROM audit_log WHERE entity_id = ? ORDER BY created_at",
                orderId
        );
        context.put("db.audit.results", rows);
        log.info("Found {} audit entries", rows.size());
    }

    @When("I confirm the order via the API")
    public void confirmOrder() {
        // Delegate to API steps - this triggers the DB update
        ApiSteps apiSteps = new ApiSteps();
        apiSteps.submitPut("/api/orders/{created.order.id}/confirm");
    }

    @When("I cancel the order via the API")
    public void cancelOrder() {
        ApiSteps apiSteps = new ApiSteps();
        apiSteps.submitPut("/api/orders/{created.order.id}/cancel");
    }

    // ===== ASSERTION STEPS =====

    @Then("the database record should have:")
    public void databaseRecordShouldHave(DataTable dataTable) {
        @SuppressWarnings("unchecked")
        Map<String, Object> row = (Map<String, Object>) context.get("db.query.result");
        assertThat(row).as("No database record found").isNotNull();

        Map<String, String> expected = dataTable.asMap(String.class, String.class);
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            String column = entry.getKey();
            String expectedValue = resolvePlaceholder(entry.getValue());
            String actualValue = String.valueOf(row.get(column));

            assertThat(actualValue)
                    .as("Column '%s' should be '%s'", column, expectedValue)
                    .isEqualTo(expectedValue);
        }
    }

    @Then("the total_amount should match the calculated order total")
    public void totalAmountMatchesCalculated() {
        @SuppressWarnings("unchecked")
        Map<String, Object> row = (Map<String, Object>) context.get("db.query.result");
        Order order = context.getCurrentDataSet().getOrders().get(0);

        BigDecimal dbAmount = new BigDecimal(String.valueOf(row.get("total_amount")));
        assertThat(dbAmount.compareTo(order.getTotalAmount()))
                .as("DB total %s should match calculated total %s", dbAmount, order.getTotalAmount())
                .isEqualTo(0);
    }

    @Then("the number of item records should match the order item count")
    public void itemCountMatches() {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) context.get("db.query.results");
        int expectedCount = context.getCurrentDataSet().getOrders().get(0).getItems().size();

        assertThat(rows)
                .as("Should have %d item records", expectedCount)
                .hasSize(expectedCount);
    }

    @Then("each item record should reference the correct order ID")
    public void itemsReferenceCorrectOrder() {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) context.get("db.query.results");
        String orderId = (String) context.get("created.order.id");

        for (Map<String, Object> row : rows) {
            assertThat(String.valueOf(row.get("order_id")))
                    .as("Item should reference order %s", orderId)
                    .isEqualTo(orderId);
        }
    }

    @Then("the updated_at timestamp should be after the created_at timestamp")
    public void updatedAfterCreated() {
        @SuppressWarnings("unchecked")
        Map<String, Object> row = (Map<String, Object>) context.get("db.query.result");

        Timestamp createdAt = (Timestamp) row.get("created_at");
        Timestamp updatedAt = (Timestamp) row.get("updated_at");

        assertThat(updatedAt)
                .as("updated_at should be after created_at")
                .isAfter(createdAt);
    }

    @Then("there should be an audit entry with action {string}")
    public void auditEntryExists(String action) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) context.get("db.audit.results");

        boolean found = rows.stream()
                .anyMatch(r -> action.equals(String.valueOf(r.get("action"))));

        assertThat(found)
                .as("Should have audit entry with action '%s'", action)
                .isTrue();
    }

    @Then("the audit entry should reference the correct user ID")
    public void auditReferencesUser() {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) context.get("db.audit.results");
        String expectedUserId = context.getCurrentDataSet().getUser().getId();

        boolean found = rows.stream()
                .anyMatch(r -> expectedUserId.equals(String.valueOf(r.get("user_id"))));

        assertThat(found)
                .as("Audit should reference user %s", expectedUserId)
                .isTrue();
    }

    @Then("the orders table should show status {string} for the order")
    public void orderStatusIs(String expectedStatus) {
        DatabaseHelper db = context.getDbHelper();
        String orderId = (String) context.get("created.order.id");

        Map<String, Object> row = db.queryRow("SELECT status FROM orders WHERE id = ?", orderId);
        assertThat(String.valueOf(row.get("status")))
                .isEqualTo(expectedStatus);
    }

    @Then("no active shipment records should exist for the order")
    public void noActiveShipments() {
        DatabaseHelper db = context.getDbHelper();
        String orderId = (String) context.get("created.order.id");

        long count = db.count("shipments", "order_id = ? AND status != 'CANCELLED'", orderId);
        assertThat(count).as("Should have no active shipments").isEqualTo(0);
    }

    private String resolvePlaceholder(String value) {
        if ("{generated.user.id}".equals(value)) {
            return context.getCurrentDataSet().getUser().getId();
        }
        return value;
    }
}
