@kafka @trade
Feature: Trade & Settlement Kafka Events
  As a QA engineer, I want to verify that trade executions and settlement
  state changes produce correct Kafka events on the appropriate topics.

  Background:
    Given trade test data is loaded for scenario "trade-happy-path"
    And Kafka consumers are listening on topics:
      | trade.events      |
      | settlement.events |

  Scenario: Trade execution produces TRADE_EXECUTED event
    Given a trade has been submitted for "AAPL"
    And the submitted trade events are published to Kafka
    Then within 10 seconds a Kafka event should appear on "trade.events" with:
      | field      | expected        |
      | eventType  | TRADE_EXECUTED  |
    And the event payload should contain field "tradeId"
    And the event payload should contain field "symbol"
    And the event payload should contain field "price"
    And the event payload should contain field "exchange"

  Scenario: Settlement creation produces SETTLEMENT_CREATED event
    Given a trade has been submitted for "AAPL"
    And the submitted trade events are published to Kafka
    Then within 10 seconds a Kafka event should appear on "settlement.events" with:
      | field      | expected             |
      | eventType  | SETTLEMENT_CREATED   |
    And the event payload should contain field "settlementId"
    And the event payload should contain field "clearingHouse"
    And the event payload should contain field "counterpartyId"

  Scenario: Completed settlement produces full event chain
    Given a trade has been submitted for "MSFT"
    And the submitted trade events are published to Kafka
    Then the Kafka events on "settlement.events" should appear in order:
      | SETTLEMENT_CREATED   |
      | SETTLEMENT_MATCHED   |
      | SETTLEMENT_CLEARED   |
      | SETTLEMENT_COMPLETED |

  @negative
  Scenario: Rejected trade does not produce settlement events
    Given a rejected trade exists in the data set
    And the rejected trade event is published to Kafka
    Then within 5 seconds a Kafka event should appear on "trade.events" with:
      | field      | expected        |
      | eventType  | TRADE_REJECTED  |
    And no Kafka event should appear on "settlement.events" within 5 seconds
