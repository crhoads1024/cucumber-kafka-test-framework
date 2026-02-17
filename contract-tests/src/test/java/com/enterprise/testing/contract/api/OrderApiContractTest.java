package com.enterprise.testing.contract.api;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pact Consumer Contract Tests for the Order API.
 * 
 * HOW CONTRACT TESTING WORKS:
 * 
 * 1. CONSUMER SIDE (this file):
 *    - Define what you EXPECT the API to look like
 *    - Pact creates a mock server matching your expectations
 *    - Your test code calls the mock server
 *    - Pact generates a "pact file" (contract) from this interaction
 * 
 * 2. PROVIDER SIDE (runs against the real API):
 *    - Pact replays the recorded interactions against the real API
 *    - Verifies the real API matches what the consumer expects
 * 
 * HOW TO WRITE THESE:
 * - @Pact method defines the expected interaction (request + response)
 * - @Test method exercises the mock server and verifies your client code works
 * - Use PactDslJsonBody for flexible response matching (type-based, not value-based)
 * - One @Pact + @Test pair per interaction scenario
 */
@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "OrderService")
public class OrderApiContractTest {

    /**
     * Contract: Creating an order returns 201 with order details.
     */
    @Pact(consumer = "OrderConsumer")
    public V4Pact createOrderPact(PactDslWithProvider builder) {
        return builder
                .given("the order service is available")
                .uponReceiving("a request to create an order")
                .path("/api/orders")
                .method("POST")
                .headers(Map.of("Content-Type", "application/json"))
                .body(new PactDslJsonBody()
                        .stringType("customerId", "cust-123")
                        .minArrayLike("items", 1)
                            .stringType("productId", "prod-001")
                            .stringType("productName", "Test Product")
                            .integerType("quantity", 1)
                            .decimalType("unitPrice", 29.99)
                            .closeObject()
                        .closeArray()
                )
                .willRespondWith()
                .status(201)
                .headers(Map.of("Content-Type", "application/json"))
                .body(new PactDslJsonBody()
                        .stringType("id")                          // Any string
                        .stringValue("status", "PENDING")          // Exact value
                        .stringType("customerId")                  // Any string
                        .decimalType("totalAmount")                // Any decimal
                        .minArrayLike("items", 1)                  // At least 1 item
                            .stringType("productId")
                            .closeObject()
                        .closeArray()
                        .stringMatcher("createdAt",                // ISO datetime pattern
                                "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}.*",
                                "2026-01-15T10:30:00Z")
                )
                .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "createOrderPact")
    void testCreateOrder(MockServer mockServer) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        String requestBody = """
                {
                    "customerId": "cust-123",
                    "items": [
                        {"productId": "prod-001", "productName": "Test Product", "quantity": 1, "unitPrice": 29.99}
                    ]
                }
                """;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(mockServer.getUrl() + "/api/orders"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body()).contains("PENDING");
    }

    /**
     * Contract: Getting an order by ID returns 200 with full order details.
     */
    @Pact(consumer = "OrderConsumer")
    public V4Pact getOrderByIdPact(PactDslWithProvider builder) {
        return builder
                .given("an order with ID order-abc-123 exists")
                .uponReceiving("a request to get order by ID")
                .path("/api/orders/order-abc-123")
                .method("GET")
                .willRespondWith()
                .status(200)
                .headers(Map.of("Content-Type", "application/json"))
                .body(new PactDslJsonBody()
                        .stringValue("id", "order-abc-123")
                        .stringType("customerId")
                        .stringType("status")
                        .decimalType("totalAmount")
                        .eachLike("items")
                            .stringType("productId")
                            .stringType("productName")
                            .integerType("quantity")
                            .decimalType("unitPrice")
                            .closeObject()
                        .closeArray()
                )
                .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "getOrderByIdPact")
    void testGetOrderById(MockServer mockServer) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(mockServer.getUrl() + "/api/orders/order-abc-123"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("order-abc-123");
    }

    /**
     * Contract: Non-existent order returns 404.
     */
    @Pact(consumer = "OrderConsumer")
    public V4Pact orderNotFoundPact(PactDslWithProvider builder) {
        return builder
                .given("no order exists with ID nonexistent-999")
                .uponReceiving("a request for a non-existent order")
                .path("/api/orders/nonexistent-999")
                .method("GET")
                .willRespondWith()
                .status(404)
                .body(new PactDslJsonBody()
                        .stringType("error")
                        .stringType("message")
                )
                .toPact(V4Pact.class);
    }

    @Test
    @PactTestFor(pactMethod = "orderNotFoundPact")
    void testOrderNotFound(MockServer mockServer) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(mockServer.getUrl() + "/api/orders/nonexistent-999"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(404);
    }
}
