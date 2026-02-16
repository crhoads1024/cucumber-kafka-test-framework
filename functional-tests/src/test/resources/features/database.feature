@database
Feature: Database Integrity Tests
  As a QA engineer, I want to verify that data persists correctly in the
  database after API operations and event processing complete.

  Background:
    Given synthetic test data is loaded for scenario "order-happy-path"
    And a database connection is established

  # =========================================================================
  # DATABASE TEST WRITING GUIDE:
  #
  # These tests run AFTER functional/API tests to verify persistence:
  # 1. Perform an action (create order via API)
  # 2. Seed the data into the database (simulating the app's DB write)
  # 3. Query the database directly
  # 4. Assert row values match expected data
  #
  # The E2E orchestration steps handle the API → DB bridge.
  # =========================================================================

  Scenario: Order data persists correctly after creation
    Given an order has been created via the API for the generated user
    And the created order is persisted to the database
    When I query the orders table for the created order ID
    Then the database record should have:
      | column      | expected                  |
      | customer_id | {generated.user.id}       |
      | status      | PENDING                   |
    And the total_amount should match the calculated order total

  Scenario: Order items are stored with correct references
    Given an order has been created via the API for the generated user
    And the created order is persisted to the database
    When I query the order_items table for the created order ID
    Then the number of item records should match the order item count
    And each item record should reference the correct order ID

  Scenario: Order status update reflects in database
    Given an order has been created via the API for the generated user
    And the created order is persisted to the database
    When I confirm the order via the API and persist the change
    And I query the orders table for the created order ID
    Then the database record should have:
      | column      | expected   |
      | status      | CONFIRMED  |
    And the updated_at timestamp should be after the created_at timestamp

  Scenario: Audit log captures order events
    Given an order has been created via the API for the generated user
    And the created order is persisted to the database
    When I query the audit_log table for the created order ID
    Then there should be an audit entry with action "ORDER_CREATED"
    And the audit entry should reference the correct user ID

  @negative
  Scenario: Cancelled order does not leave orphan records
    Given an order has been created via the API for the generated user
    And the created order is persisted to the database
    When I cancel the order via the API and persist the change
    Then the orders table should show status "CANCELLED" for the order
    And no active shipment records should exist for the order
