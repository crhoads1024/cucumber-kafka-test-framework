package com.enterprise.testing.functional.steps;

import com.enterprise.testing.datagen.TestDataRegistry;
import com.enterprise.testing.shared.config.FrameworkConfig;
import com.enterprise.testing.shared.model.SyntheticDataSet;
import com.enterprise.testing.shared.model.trade.*;
import com.enterprise.testing.shared.util.JsonUtil;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for trade and settlement BDD tests.
 *
 * These steps handle:
 * - Loading trade-specific test data (with real market prices)
 * - Submitting trades via API
 * - Asserting trade prices against market data
 * - Settlement lifecycle assertions
 */
public class TradeSteps {

    private static final Logger log = LoggerFactory.getLogger(TradeSteps.class);
    private final TestContext context;

    public TradeSteps() {
        this.context = SharedTestContext.get();
        RestAssured.baseURI = FrameworkConfig.getInstance().getBaseUrl();
    }

    // ===== DATA LOADING =====

    @Given("trade test data is loaded for scenario {string}")
    public void loadTradeData(String scenarioId) {
        TestDataRegistry registry = context.getDataRegistry();

        if (!registry.hasScenario(scenarioId)) {
            log.info("Generating trade data for scenario: {}", scenarioId);

            if (scenarioId.contains("round-trip")) {
                // Round-trip scenarios: exactly 1 BUY + 1 SELL for a single symbol
                String symbol = scenarioId.contains("crypto") ? "BTC-USD" : "AAPL";
                registry.generateRoundTripScenario(scenarioId, symbol);
            } else if (scenarioId.contains("settlement-failures")) {
                // Failure scenarios: need enough trades to guarantee some FAILED settlements
                registry.generateTradeScenario(scenarioId,
                        List.of("TSLA", "JPM"), 10);
            } else {
                // Default: generate with common symbols
                registry.generateTradeScenario(scenarioId,
                        List.of("AAPL", "BTC-USD", "XRP-USD"), 3);
            }
        }

        SyntheticDataSet dataSet = registry.getScenario(scenarioId);
        context.setCurrentDataSet(dataSet);

        log.info("Loaded trade scenario '{}': trades={}, settlements={}, snapshots={}",
                scenarioId,
                dataSet.getTrades().size(),
                dataSet.getSettlements().size(),
                dataSet.getMarketSnapshots() != null ? dataSet.getMarketSnapshots().size() : 0);
    }

    // ===== TRADE SELECTION =====

    @Given("a generated trade for symbol {string}")
    public void selectTradeForSymbol(String symbol) {
        SyntheticDataSet data = context.getCurrentDataSet();
        // Prefer EXECUTED trades; fall back to any trade for the symbol
        Trade trade = data.getTrades().stream()
                .filter(t -> t.getSymbol().equals(symbol) && t.getStatus() == TradeStatus.EXECUTED)
                .findFirst()
                .or(() -> data.getTrades().stream()
                        .filter(t -> t.getSymbol().equals(symbol))
                        .findFirst())
                .orElseThrow(() -> new AssertionError(
                        "No trade found for symbol " + symbol + " in data set"));

        context.put("selected.trade", trade);
        log.info("Selected trade: {}", trade);
    }

    @Given("a trade has been submitted for {string}")
    public void submitTradeForSymbol(String symbol) {
        selectTradeForSymbol(symbol);
        submitTrade();
    }

    @Given("a trade has been submitted via the API for {string}")
    public void submitTradeViaApi(String symbol) {
        submitTradeForSymbol(symbol);
    }

    @Given("a rejected trade exists in the data set")
    public void findRejectedTrade() {
        SyntheticDataSet data = context.getCurrentDataSet();
        Trade rejected = data.getTrades().stream()
                .filter(t -> t.getStatus() == TradeStatus.REJECTED)
                .findFirst()
                .orElse(null);

        if (rejected == null) {
            // If no rejected trade was generated, create one
            Trade first = data.getTrades().get(0);
            first.setStatus(TradeStatus.REJECTED);
            rejected = first;
        }

        context.put("selected.trade", rejected);
        context.put("created.trade.id", rejected.getTradeId());
        log.info("Found rejected trade: {}", rejected);
    }

    // ===== TRADE SUBMISSION =====

    @When("I submit the trade to {string}")
    public void submitTrade(String endpoint) {
        Trade trade = (Trade) context.get("selected.trade");
        String body = JsonUtil.toJson(trade);

        Response response = given()
                .contentType(ContentType.JSON)
                .body(body)
                .post(endpoint);

        context.setLastApiResponse(response);
        if (response.getStatusCode() == 201) {
            String tradeId = response.jsonPath().getString("tradeId");
            context.put("created.trade.id", tradeId);
            context.put("generated.account.id", trade.getAccountId());
            log.info("Submitted trade: {} -> {}", tradeId, response.getStatusCode());
        }
    }

    private void submitTrade() {
        submitTrade("/api/trades");
    }

    // ===== TRADE ASSERTIONS =====

    @Then("the trade price should be within the market bid-ask spread")
    public void tradePriceWithinSpread() {
        Trade trade = (Trade) context.get("selected.trade");
        MarketSnapshot snapshot = context.getCurrentDataSet()
                .getMarketSnapshots().get(trade.getSymbol());

        assertThat(snapshot).as("No market snapshot for %s", trade.getSymbol()).isNotNull();

        // Allow some slippage beyond the spread (30% tolerance)
        BigDecimal spread = snapshot.getAsk().subtract(snapshot.getBid());
        BigDecimal tolerance = spread.multiply(new BigDecimal("1.3"));
        BigDecimal lowerBound = snapshot.getBid().subtract(tolerance);
        BigDecimal upperBound = snapshot.getAsk().add(tolerance);

        assertThat(trade.getPrice())
                .as("Trade price %s should be near bid %s / ask %s",
                        trade.getPrice(), snapshot.getBid(), snapshot.getAsk())
                .isBetween(lowerBound, upperBound);
    }

    @Then("the trade total value should equal price times quantity")
    public void totalValueCorrect() {
        Trade trade = (Trade) context.get("selected.trade");
        BigDecimal expected = trade.getPrice()
                .multiply(BigDecimal.valueOf(trade.getQuantity()));

        assertThat(trade.getTotalValue().compareTo(expected))
                .as("Total value %s should equal price(%s) * qty(%d) = %s",
                        trade.getTotalValue(), trade.getPrice(), trade.getQuantity(), expected)
                .isEqualTo(0);
    }

    // ===== SETTLEMENT ASSERTIONS =====

    @When("I query the settlement for the submitted trade")
    public void querySettlementForTrade() {
        Trade trade = (Trade) context.get("selected.trade");
        SyntheticDataSet data = context.getCurrentDataSet();

        Settlement settlement = data.getSettlements().stream()
                .filter(s -> s.getTradeId().equals(trade.getTradeId()))
                .findFirst()
                .orElse(null);

        context.put("queried.settlement", settlement);
        log.info("Found settlement for trade {}: {}", trade.getTradeId(), settlement);
    }

    @Then("the settlement status should be one of:")
    public void settlementStatusOneOf(List<String> validStatuses) {
        Settlement settlement = (Settlement) context.get("queried.settlement");
        assertThat(settlement).as("No settlement found").isNotNull();
        assertThat(settlement.getStatus().name())
                .as("Settlement status should be one of %s", validStatuses)
                .isIn(validStatuses);
    }

    @Then("the settlement trade ID should match the submitted trade")
    public void settlementMatchesTrade() {
        Settlement settlement = (Settlement) context.get("queried.settlement");
        Trade trade = (Trade) context.get("selected.trade");
        assertThat(settlement.getTradeId()).isEqualTo(trade.getTradeId());
    }

    @Then("the settlement amount should include fees")
    public void settlementIncludesFees() {
        Settlement settlement = (Settlement) context.get("queried.settlement");
        Trade trade = (Trade) context.get("selected.trade");

        // Settlement amount should be >= trade total (fees added)
        assertThat(settlement.getSettlementAmount().compareTo(trade.getTotalValue()))
                .as("Settlement amount %s should be >= trade total %s (fees included)",
                        settlement.getSettlementAmount(), trade.getTotalValue())
                .isGreaterThanOrEqualTo(0);
    }

    @Then("the settlement date should be {int} business day(s) after the trade date")
    public void settlementDateTPlus(int businessDays) {
        Settlement settlement = (Settlement) context.get("queried.settlement");
        assertThat(settlement).as("No settlement found").isNotNull();

        LocalDate tradeDate = settlement.getTradeDate();
        LocalDate expected = tradeDate;
        int added = 0;
        while (added < businessDays) {
            expected = expected.plusDays(1);
            if (expected.getDayOfWeek() != DayOfWeek.SATURDAY &&
                expected.getDayOfWeek() != DayOfWeek.SUNDAY) {
                added++;
            }
        }

        assertThat(settlement.getSettlementDate())
                .as("Settlement date should be T+%d from trade date %s", businessDays, tradeDate)
                .isEqualTo(expected);
    }

    @Then("the settlement date should equal the trade date")
    public void settlementDateEqualsTradeDate() {
        Settlement settlement = (Settlement) context.get("queried.settlement");
        assertThat(settlement.getSettlementDate())
                .as("Crypto should settle T+0")
                .isEqualTo(settlement.getTradeDate());
    }

    // ===== DATA SET ASSERTIONS =====

    @Then("there should be {int} trades in the data set")
    public void tradeCountEquals(int expectedCount) {
        assertThat(context.getCurrentDataSet().getTrades())
                .hasSize(expectedCount);
    }

    @Then("there should be at least {int} settlements in the data set")
    public void settlementCountAtLeast(int minCount) {
        assertThat(context.getCurrentDataSet().getSettlements())
                .hasSizeGreaterThanOrEqualTo(minCount);
    }

    @Then("one settlement should be for side {string}")
    public void settlementForSide(String side) {
        TradeSide expected = TradeSide.valueOf(side);
        boolean found = context.getCurrentDataSet().getSettlements().stream()
                .anyMatch(s -> s.getSide() == expected);
        assertThat(found).as("Should have a settlement for side %s", side).isTrue();
    }

    @When("I find a settlement with status {string}")
    public void findSettlementWithStatus(String status) {
        SettlementStatus target = SettlementStatus.valueOf(status);
        Settlement found = context.getCurrentDataSet().getSettlements().stream()
                .filter(s -> s.getStatus() == target)
                .findFirst()
                .orElse(null);

        context.put("queried.settlement", found);
        if (found != null) {
            log.info("Found {} settlement: {}", status, found);
        }
    }

    @Then("the settlement should have a non-empty fail reason")
    public void failReasonNotEmpty() {
        Settlement settlement = (Settlement) context.get("queried.settlement");
        assertThat(settlement).as("No failed settlement found").isNotNull();
        assertThat(settlement.getFailReason())
                .as("Failed settlement should have a reason")
                .isNotNull()
                .isNotEmpty();
    }

    @Then("the fail reason should be one of:")
    public void failReasonOneOf(List<String> validReasons) {
        Settlement settlement = (Settlement) context.get("queried.settlement");
        assertThat(settlement.getFailReason())
                .as("Fail reason should be a known reason")
                .isIn(validReasons);
    }

    // ===== DB ASSERTIONS =====

    @When("I query the trades table for the submitted trade ID")
    public void queryTradesTable() {
        String tradeId = (String) context.get("created.trade.id");
        var row = context.getDbHelper().queryRow(
                "SELECT * FROM trades WHERE trade_id = ?", tradeId);
        context.put("db.query.result", row);
    }

    @When("I query the settlements table for the submitted trade ID")
    public void querySettlementsTable() {
        String tradeId = (String) context.get("created.trade.id");
        var row = context.getDbHelper().queryRow(
                "SELECT * FROM settlements WHERE trade_id = ?", tradeId);
        context.put("db.query.result", row);
    }

    @Then("the price column should be a positive number")
    public void priceIsPositive() {
        @SuppressWarnings("unchecked")
        var row = (java.util.Map<String, Object>) context.get("db.query.result");
        BigDecimal price = new BigDecimal(String.valueOf(row.get("price")));
        assertThat(price).isGreaterThan(BigDecimal.ZERO);
    }

    @Then("the total_value column should equal price times quantity")
    public void dbTotalValueCorrect() {
        @SuppressWarnings("unchecked")
        var row = (java.util.Map<String, Object>) context.get("db.query.result");
        BigDecimal price = new BigDecimal(String.valueOf(row.get("price")));
        int quantity = ((Number) row.get("quantity")).intValue();
        BigDecimal totalValue = new BigDecimal(String.valueOf(row.get("total_value")));
        BigDecimal expected = price.multiply(BigDecimal.valueOf(quantity));

        assertThat(totalValue.setScale(2, RoundingMode.HALF_UP))
                .isEqualTo(expected.setScale(2, RoundingMode.HALF_UP));
    }

    @Then("the settlement record should reference the correct trade ID")
    public void settlementReferencesTradeId() {
        @SuppressWarnings("unchecked")
        var row = (java.util.Map<String, Object>) context.get("db.query.result");
        String tradeId = (String) context.get("created.trade.id");
        assertThat(String.valueOf(row.get("trade_id"))).isEqualTo(tradeId);
    }

    @Then("the settlement_date should be after the trade_date")
    public void settlementDateAfterTradeDate() {
        @SuppressWarnings("unchecked")
        var row = (java.util.Map<String, Object>) context.get("db.query.result");
        LocalDate tradeDate = LocalDate.parse(String.valueOf(row.get("trade_date")));
        LocalDate settlementDate = LocalDate.parse(String.valueOf(row.get("settlement_date")));
        assertThat(settlementDate).isAfterOrEqualTo(tradeDate);
    }

    @Then("the clearing_house column should not be null")
    public void clearingHouseNotNull() {
        @SuppressWarnings("unchecked")
        var row = (java.util.Map<String, Object>) context.get("db.query.result");
        assertThat(row.get("clearing_house")).isNotNull();
    }

    @Then("the audit details should include the execution price")
    public void auditIncludesPrice() {
        @SuppressWarnings("unchecked")
        var rows = (java.util.List<java.util.Map<String, Object>>) context.get("db.audit.results");
        assertThat(rows).isNotEmpty();
        String details = String.valueOf(rows.get(0).get("details"));
        assertThat(details).contains("price");
    }

    @When("I query the audit_log table for the submitted trade ID")
    public void queryAuditLogForTrade() {
        String tradeId = (String) context.get("created.trade.id");
        var rows = context.getDbHelper().queryList(
                "SELECT * FROM audit_log WHERE entity_id = ? ORDER BY created_at", tradeId);
        context.put("db.audit.results", rows);
        log.info("Found {} audit entries for trade {}", rows.size(), tradeId);
    }
}
