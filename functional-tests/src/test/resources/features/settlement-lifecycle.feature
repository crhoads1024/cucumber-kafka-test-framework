@functional @settlement
Feature: Settlement Lifecycle
  As a QA engineer, I want to verify that executed trades produce
  correct settlement records with proper T+1 dating, clearing house
  assignment, and status progression.

  Background:
    Given trade test data is loaded for scenario "trade-happy-path"

  @smoke
  Scenario: Executed trade creates a settlement record
    Given a trade has been submitted for "AAPL"
    When I query the settlement for the submitted trade
    Then the settlement status should be one of:
      | PENDING  |
      | MATCHED  |
      | CLEARING |
      | SETTLED  |
    And the settlement trade ID should match the submitted trade
    And the settlement amount should include fees

  Scenario: Settlement date is T+1 for US equities
    Given a trade has been submitted for "AAPL"
    When I query the settlement for the submitted trade
    Then the settlement date should be 1 business day after the trade date

  Scenario: Crypto trades settle same day (T+0)
    Given a trade has been submitted for "BTC-USD"
    When I query the settlement for the submitted trade
    Then the settlement date should equal the trade date

  Scenario: Round-trip trade produces two settlements
    Given trade test data is loaded for scenario "trade-round-trip-equity"
    Then there should be 2 trades in the data set
    And there should be at least 2 settlements in the data set
    And one settlement should be for side "BUY"
    And one settlement should be for side "SELL"

  @negative
  Scenario: Failed settlement has a failure reason
    Given trade test data is loaded for scenario "trade-settlement-failures"
    When I find a settlement with status "FAILED"
    Then the settlement should have a non-empty fail reason
    And the fail reason should be one of:
      | INSUFFICIENT_SECURITIES   |
      | COUNTERPARTY_DEFAULT      |
      | MISMATCHED_TRADE_DETAILS  |
      | FAILED_DELIVERY           |
      | REGULATORY_HOLD           |
      | INSUFFICIENT_MARGIN       |
      | SYSTEM_ERROR              |
      | LATE_AFFIRMATION          |
