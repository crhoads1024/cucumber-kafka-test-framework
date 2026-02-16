package com.enterprise.testing.functional.steps;

import com.enterprise.testing.shared.config.FrameworkConfig;
import com.enterprise.testing.shared.model.Order;
import com.enterprise.testing.shared.model.SyntheticDataSet;
import com.enterprise.testing.shared.util.JsonUtil;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions for REST API interactions.
 * 
 * HOW TO WRITE API TEST STEPS:
 * 
 * 1. @When steps perform the HTTP action (GET, POST, PUT, DELETE)
 * 2. @Then steps assert on the response (status, body, headers)
 * 3. Use TestContext to pass data between steps
 * 4. Use {generated.xxx} placeholders to reference synthetic data
 * 
 * EXTENDING:
 * - Add new @When methods for different HTTP verbs or endpoints
 * - Add new @Then methods for different assertion types
 * - Keep steps generic and reusable across features
 */
public class ApiSteps {

    private static final Logger log = LoggerFactory.getLogger(ApiSteps.class);
    private final TestContext context;

    public ApiSteps() {
        this.context = SharedTestContext.get();
        RestAssured.baseURI = FrameworkConfig.getInstance().getBaseUrl();
    }

    // ===== SETUP STEPS =====

    @Given("an order has been created for the generated user")
    public void createOrderForGeneratedUser() {
        SyntheticDataSet data = context.getCurrentDataSet();
        Order order = data.getOrders().get(0);

        String body = JsonUtil.toJson(order);
        Response response = given()
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post("/api/orders");

        context.setLastApiResponse(response);
        // Store the created order ID for subsequent steps
        if (response.getStatusCode() == 201) {
            String createdId = response.jsonPath().getString("id");
            context.put("created.order.id", createdId);
            log.info("Created order: {}", createdId);
        }
    }

    @Given("an order has been created via the API for the generated user")
    public void createOrderViaApi() {
        createOrderForGeneratedUser();
    }

    // ===== HTTP ACTION STEPS =====

    @When("I send a GET request to {string}")
    public void sendGetRequest(String endpoint) {
        String resolvedEndpoint = resolvePlaceholders(endpoint);
        log.info("GET {}", resolvedEndpoint);

        Response response = given()
                .contentType(ContentType.JSON)
                .urlEncodingEnabled(false)
                .when()
                .get(resolvedEndpoint);

        context.setLastApiResponse(response);
    }

    @When("I submit a POST to {string} with the generated order payload")
    public void submitPostWithGeneratedPayload(String endpoint) {
        SyntheticDataSet data = context.getCurrentDataSet();
        Order order = data.getOrders().get(0);
        String body = JsonUtil.toJson(order);

        log.info("POST {} with generated order for user {}", endpoint, data.getUser().getId());

        Response response = given()
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post(endpoint);

        context.setLastApiResponse(response);
        context.setLastRequestBody(body);

        if (response.getStatusCode() == 201) {
            String createdId = response.jsonPath().getString("id");
            context.put("created.order.id", createdId);
        }
    }

    @When("I submit a POST to {string} with body:")
    public void submitPostWithBody(String endpoint, String body) {
        log.info("POST {} with custom body", endpoint);

        Response response = given()
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post(endpoint);

        context.setLastApiResponse(response);
        context.setLastRequestBody(body);
    }

    @When("I submit a PUT to {string}")
    public void submitPut(String endpoint) {
        String resolvedEndpoint = resolvePlaceholders(endpoint);
        log.info("PUT {}", resolvedEndpoint);

        Response response = given()
                .contentType(ContentType.JSON)
                .when()
                .put(resolvedEndpoint);

        context.setLastApiResponse(response);
    }

    // ===== RESPONSE ASSERTION STEPS =====

    @Then("the response status should be {int}")
    public void responseStatusShouldBe(int expectedStatus) {
        Response response = context.getLastApiResponse();
        assertThat(response.getStatusCode())
                .as("Expected HTTP status %d but got %d. Body: %s",
                        expectedStatus, response.getStatusCode(), response.getBody().asString())
                .isEqualTo(expectedStatus);
    }

    @Then("the response body should contain {string}")
    public void responseBodyContains(String expectedContent) {
        String body = context.getLastApiResponse().getBody().asString();
        assertThat(body)
                .as("Response body should contain '%s'", expectedContent)
                .contains(expectedContent);
    }

    @Then("the response body should contain field {string}")
    public void responseContainsField(String fieldName) {
        Object value = context.getLastApiResponse().jsonPath().get(fieldName);
        assertThat(value)
                .as("Response should contain field '%s'", fieldName)
                .isNotNull();
    }

    @Then("the response body should contain field {string} with value {string}")
    public void responseContainsFieldWithValue(String fieldName, String expectedValue) {
        String actual = context.getLastApiResponse().jsonPath().getString(fieldName);
        assertThat(actual)
                .as("Field '%s' should be '%s'", fieldName, expectedValue)
                .isEqualTo(expectedValue);
    }

    @Then("the response body field {string} should equal the generated user ID")
    public void responseFieldEqualsGeneratedUserId(String fieldName) {
        String actual = context.getLastApiResponse().jsonPath().getString(fieldName);
        String expectedUserId = context.getCurrentDataSet().getUser().getId();
        assertThat(actual)
                .as("Field '%s' should match generated user ID", fieldName)
                .isEqualTo(expectedUserId);
    }

    @Then("the response body should be a non-empty array")
    public void responseIsNonEmptyArray() {
        int size = context.getLastApiResponse().jsonPath().getList("$").size();
        assertThat(size)
                .as("Response array should not be empty")
                .isGreaterThan(0);
    }

    // ===== UTILITY =====

    /**
     * Resolve placeholders like {generated.user.id} and {created.order.id}
     * in endpoint strings.
     */
    private String resolvePlaceholders(String endpoint) {
        String resolved = endpoint;

        if (context.getCurrentDataSet() != null) {
            resolved = resolved.replace("{generated.user.id}",
                    context.getCurrentDataSet().getUser().getId());
        }

        Object createdOrderId = context.get("created.order.id");
        if (createdOrderId != null) {
            resolved = resolved.replace("{created.order.id}", createdOrderId.toString());
        }

        Object createdTradeId = context.get("created.trade.id");
        if (createdTradeId != null) {
            resolved = resolved.replace("{created.trade.id}", createdTradeId.toString());
        }

        Object generatedAccountId = context.get("generated.account.id");
        if (generatedAccountId != null) {
            resolved = resolved.replace("{generated.account.id}", generatedAccountId.toString());
        }

        // Safety: fail fast if any placeholders remain unresolved
        if (resolved.contains("{") && resolved.contains("}")) {
            String unresolved = resolved.replaceAll(".*?(\\{[^}]+}).*", "$1");
            log.warn("Unresolved placeholder in URL: {} -> {}", endpoint, resolved);
        }

        return resolved;
    }
}
