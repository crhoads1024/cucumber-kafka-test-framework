@functional @kafka
Feature: Kafka Event Processing
  As a QA engineer, I want to verify that order actions produce the correct
  Kafka events with the expected payload structure and content.

  Background:
    Given synthetic test data is loaded for scenario "order-happy-path"
    And Kafka consumers are listening on topics:
      | order.events        |
      | notification.events |

  # =========================================================================
  # KAFKA TEST WRITING GUIDE:
  #
  # These tests verify the event-driven side of the system:
  # 1. Action (API call) -> Orchestration publishes Kafka events
  # 2. Consumer captures events on subscribed topics
  # 3. awaitEvent() polls captured events with a timeout
  # 4. Assert payload matches expected structure and values
  # =========================================================================

  Scenario: Order creation produces ORDER_CREATED event
    When I submit a POST to "/api/orders" with the generated order payload
    And the created order events are published to Kafka
    Then within 10 seconds a Kafka event should appear on "order.events" with:
      | field      | expected      |
      | eventType  | ORDER_CREATED |
    And the event payload should contain field "orderId"
    And the event payload should contain field "customerId" matching the generated user ID

  Scenario: Order confirmation produces multiple events
    Given an order has been created for the generated user
    And the created order events are published to Kafka
    When I confirm the order via the API and persist the change
    Then within 10 seconds a Kafka event should appear on "order.events" with:
      | field      | expected        |
      | eventType  | ORDER_CONFIRMED |
    And within 10 seconds a Kafka event should appear on "notification.events" with:
      | field      | expected          |
      | eventType  | NOTIFICATION_SENT |

  Scenario: Verify event ordering for order lifecycle
    Given an order has been created for the generated user
    And the created order events are published to Kafka
    When I confirm the order via the API and persist the change
    Then the Kafka events on "order.events" should appear in order:
      | ORDER_CREATED   |
      | ORDER_CONFIRMED |

  @negative
  Scenario: Cancelled order does not produce notification event
    Given an order has been created for the generated user
    And the created order events are published to Kafka
    When I cancel the order via the API and persist the change
    Then within 5 seconds a Kafka event should appear on "order.events" with:
      | field      | expected         |
      | eventType  | ORDER_CANCELLED  |
    And no Kafka event should appear on "notification.events" within 5 seconds
