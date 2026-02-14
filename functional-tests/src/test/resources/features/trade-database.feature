@database @trade
Feature: Trade & Settlement Database Integrity
  As a QA engineer, I want to verify that trade and settlement data
  persists correctly in the database with proper referential integrity.

  Background:
    Given trade test data is loaded for scenario "trade-happy-path"
    And a database connection is established

  Scenario: Trade record persists with correct market data
    Given a trade has been submitted via the API for "AAPL"
    When I query the trades table for the submitted trade ID
    Then the database record should have:
      | column    | expected |
      | symbol    | AAPL     |
      | status    | EXECUTED |
    And the price column should be a positive number
    And the total_value column should equal price times quantity

  Scenario: Settlement references the correct trade
    Given a trade has been submitted via the API for "AAPL"
    When I query the settlements table for the submitted trade ID
    Then the settlement record should reference the correct trade ID
    And the settlement_date should be after the trade_date
    And the clearing_house column should not be null

  Scenario: Trade audit log captures execution details
    Given a trade has been submitted via the API for "BTC-USD"
    When I query the audit_log table for the submitted trade ID
    Then there should be an audit entry with action "TRADE_EXECUTED"
    And the audit details should include the execution price
