@functional @api
Feature: Order API Functional Tests
  As a QA engineer, I want to verify the Order API processes requests correctly
  using synthetic test data and validates both API responses and downstream effects.

  Background:
    Given synthetic test data is loaded for scenario "order-happy-path"

  # =========================================================================
  # HOW TO WRITE THESE TESTS:
  #
  # 1. Each scenario should test ONE behavior
  # 2. Background sets up shared preconditions (data loading)
  # 3. Use tags to control which layer runs: @functional, @kafka, @database
  # 4. Reference synthetic data with placeholders: {generated.user.id}
  # 5. Keep scenarios independent - no ordering dependencies
  # =========================================================================

  @smoke
  Scenario: Health check endpoint is available
    When I send a GET request to "/actuator/health"
    Then the response status should be 200
    And the response body should contain "status"

  @smoke
  Scenario: Create a new order successfully
    When I submit a POST to "/api/orders" with the generated order payload
    Then the response status should be 201
    And the response body should contain field "id"
    And the response body should contain field "status" with value "PENDING"

  Scenario: Retrieve an existing order by ID
    Given an order has been created for the generated user
    When I send a GET request to "/api/orders/{created.order.id}"
    Then the response status should be 200
    And the response body field "customerId" should equal the generated user ID

  Scenario: List orders for a customer
    Given an order has been created for the generated user
    When I send a GET request to "/api/orders?customerId={generated.user.id}"
    Then the response status should be 200
    And the response body should be a non-empty array

  @negative
  Scenario: Reject order with missing required fields
    When I submit a POST to "/api/orders" with body:
      """
      {
        "items": []
      }
      """
    Then the response status should be 400
    And the response body should contain field "error"

  @negative
  Scenario: Return 404 for non-existent order
    When I send a GET request to "/api/orders/non-existent-id-12345"
    Then the response status should be 404
