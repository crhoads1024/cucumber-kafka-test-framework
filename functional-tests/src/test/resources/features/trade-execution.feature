@functional @trade
Feature: Trade Execution
  As a QA engineer, I want to verify the trade execution API processes
  trades correctly using real market data from Yahoo Finance and validates
  both API responses and downstream settlement creation.

  Background:
    Given trade test data is loaded for scenario "trade-happy-path"

  @smoke
  Scenario: Execute a market buy order at real market price
    Given a generated trade for symbol "AAPL"
    When I submit the trade to "/api/trades"
    Then the response status should be 201
    And the response body should contain field "tradeId"
    And the response body should contain field "status" with value "EXECUTED"
    And the trade price should be within the market bid-ask spread

  @smoke
  Scenario: Execute a crypto trade using live BTC price
    Given a generated trade for symbol "BTC-USD"
    When I submit the trade to "/api/trades"
    Then the response status should be 201
    And the trade total value should equal price times quantity

  Scenario: Retrieve trade by ID
    Given a trade has been submitted for "AAPL"
    When I send a GET request to "/api/trades/{created.trade.id}"
    Then the response status should be 200
    And the response body field "symbol" should equal "AAPL"
    And the response body should contain field "settlementId"

  Scenario: List trades for an account
    Given a trade has been submitted for "AAPL"
    When I send a GET request to "/api/trades?accountId={generated.account.id}"
    Then the response status should be 200
    And the response body should be a non-empty array

  @negative
  Scenario: Reject trade with invalid symbol
    When I submit a POST to "/api/trades" with body:
      """
      {
        "symbol": "INVALID_TICKER_XYZ",
        "side": "BUY",
        "quantity": 100,
        "orderType": "MARKET"
      }
      """
    Then the response status should be 400

  @negative
  Scenario: Reject trade with zero quantity
    When I submit a POST to "/api/trades" with body:
      """
      {
        "symbol": "AAPL",
        "side": "BUY",
        "quantity": 0,
        "orderType": "MARKET"
      }
      """
    Then the response status should be 400
