# Enterprise Test Framework

A multi-layered, BDD-driven test framework with Cucumber, Kafka, synthetic data generation, contract testing, database testing, and JMeter performance testing — orchestrated via CI/CD containers.

## Prerequisites

| Tool | Version | Required For |
|------|---------|-------------|
| Java (JDK) | 17+ | All modules |
| Maven | 3.9+ | Build and test execution |
| Docker Desktop | Latest | Local infrastructure (WireMock, Kafka, PostgreSQL) |
| Git | 2.x+ | Source control |
| JMeter | 5.x | Performance tests (Phase 3+) — optional |
| Python 3 | 3.10+ | Performance threshold validation, JSON pretty-printing — optional |

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        CI/CD PIPELINE                           │
│                                                                 │
│  ┌──────────────┐    ┌────────────────┐    ┌────────────────┐  │
│  │   Stage 1     │    │   Stage 2a     │    │   Stage 3      │  │
│  │  Data Gen     │───▶│  Functional    │───▶│  Performance   │  │
│  │  (synthetic)  │    │  + Database    │    │  (JMeter)      │  │
│  └──────────────┘    └────────────────┘    └────────────────┘  │
│         │            ┌────────────────┐           ▲             │
│         └───────────▶│   Stage 2b     │───────────┘             │
│                      │  Contract      │                         │
│                      │  (Pact+Schema) │                         │
│                      └────────────────┘                         │
└─────────────────────────────────────────────────────────────────┘
```

## Quick Start

```bash
# 1. Install shared modules to local Maven repository
mvn install -pl shared,data-generator -am -DskipTests

# 2. Generate synthetic test data
mvn exec:java -pl data-generator

# 3. Start local infrastructure (WireMock only for smoke/functional tests)
docker compose up -d wiremock
sleep 5

# 4. Run smoke tests (fastest verification)
mvn test -pl functional-tests -Dcucumber.filter.tags="@smoke"

# 5. Run contract tests (no infrastructure needed)
mvn test -pl contract-tests

# 6. Start full stack for integration tests
docker compose up -d
sleep 20

# 7. Run all functional tests
mvn test -pl functional-tests

# 8. Run performance tests (requires WireMock running)
jmeter -n -t perf-tests/test-plans/order-load-test.jmx -l results.jtl -e -o report/ \
&& python3 perf-tests/scripts/check-perf-thresholds.py results.jtl

# 9. Cleanup
docker compose down -v
```

## Infrastructure by Test Layer

| Test Layer | Tags | Infrastructure Required | Start Command |
|-----------|------|------------------------|---------------|
| Smoke | `@smoke` | WireMock | `docker compose up -d wiremock` |
| Functional | `@functional` | WireMock | `docker compose up -d wiremock` |
| Kafka | `@kafka` | WireMock + Kafka + Zookeeper | `docker compose up -d wiremock kafka zookeeper` |
| Database | `@database` | WireMock + PostgreSQL | `docker compose up -d wiremock postgres` |
| Contract | `@contract` | None | N/A |
| E2E | `@e2e` | Full stack | `docker compose up -d` |
| Performance | `@performance` | WireMock | `docker compose up -d wiremock` |

## Project Structure

```
test-framework/
├── shared/                  # Shared models, config, utilities
│   └── src/main/java/
│       ├── model/           # User, Order, KafkaEvent, etc.
│       ├── config/          # FrameworkConfig (env-aware)
│       └── util/            # JsonUtil
├── data-generator/          # Synthetic data generation
│   └── src/main/java/
│       ├── generators/      # UserGenerator, OrderGenerator, etc.
│       └── TestDataRegistry # Central data orchestrator
├── functional-tests/        # Cucumber BDD tests
│   └── src/test/
│       ├── java/steps/      # Step definitions
│       ├── java/kafka/      # KafkaTestConsumer/Producer
│       ├── java/db/         # DatabaseHelper
│       └── resources/features/  # .feature files
├── contract-tests/          # Pact + JSON Schema validation
│   └── src/test/
│       ├── java/api/        # Pact consumer tests
│       ├── java/kafka/      # Event schema validation
│       └── resources/schemas/   # JSON schemas
├── perf-tests/              # JMeter plans + threshold scripts
│   ├── test-plans/          # .jmx files
│   └── scripts/             # check-perf-thresholds.py
├── docker/                  # Dockerfiles, init scripts
├── docker-compose.yml       # Local dev infrastructure
└── .github/workflows/       # CI/CD pipeline
```

## Test Reports

| Layer | Report Path | Format |
|-------|------------|--------|
| Functional (Cucumber) | `functional-tests/target/cucumber-reports/cucumber.html` | HTML — open in browser |
| Functional (JSON) | `functional-tests/target/cucumber-reports/cucumber.json` | JSON — for CI plugins |
| Functional (Surefire) | `functional-tests/target/surefire-reports/` | JUnit XML |
| Contract (Pact) | `contract-tests/target/pacts/` | Pact JSON contracts |
| Contract (Surefire) | `contract-tests/target/surefire-reports/` | JUnit XML |
| Performance (Dashboard) | `report/index.html` | HTML — JMeter dashboard |
| Performance (Raw) | `results.jtl` | CSV — parsed by threshold script |

Quick open (macOS):
```bash
open functional-tests/target/cucumber-reports/cucumber.html
open report/index.html
```

---

# HOW TO WRITE TESTS — Layer by Layer

## Layer 1: Synthetic Data Generation

### When to Use
Create a new generator when you need a new entity type (Product, Payment, etc.) or when you need special data distributions.

### How to Write a Generator

```java
// 1. Create a new generator class in data-generator/src/main/java/.../generators/

public class PaymentGenerator {
    private final Faker faker;
    private final Random random;

    public PaymentGenerator() {
        this.faker = new Faker();
        this.random = new Random();
    }

    // Seeded constructor for reproducible data
    public PaymentGenerator(long seed) {
        this.faker = new Faker(new Random(seed));
        this.random = new Random(seed);
    }

    public Payment generate(Order order) {
        Payment payment = new Payment();
        payment.setOrderId(order.getId());
        payment.setAmount(order.getTotalAmount());
        payment.setMethod(randomMethod());
        // Make card number match the method
        if (payment.getMethod() == PaymentMethod.CREDIT_CARD) {
            payment.setCardLast4(faker.finance().creditCard().substring(12));
        }
        return payment;
    }

    // Keep distributions realistic
    private PaymentMethod randomMethod() {
        int roll = random.nextInt(100);
        if (roll < 60) return PaymentMethod.CREDIT_CARD;
        if (roll < 85) return PaymentMethod.DEBIT_CARD;
        return PaymentMethod.PAYPAL;
    }
}
```

### Rules
1. Always provide a seeded constructor for reproducible test data
2. Make fields internally consistent (VIP users get VIP-appropriate orders)
3. Generate expected Kafka events at data creation time
4. Register new scenarios in `DataGeneratorMain`

---

## Layer 2: Functional Tests (Cucumber BDD)

### How to Write a Feature File

```gherkin
# File: functional-tests/src/test/resources/features/your-feature.feature

@functional @your-tag
Feature: Your Feature Name
  As a [role], I want to [action] so that [benefit].

  Background:
    Given synthetic test data is loaded for scenario "your-scenario"

  @smoke
  Scenario: Happy path - descriptive name
    When I send a GET request to "/api/endpoint"
    Then the response status should be 200
    And the response body should contain field "expectedField"

  @negative
  Scenario: Error case - what goes wrong
    When I submit a POST to "/api/endpoint" with body:
      """
      {"invalid": "payload"}
      """
    Then the response status should be 400
```

### Writing Rules
1. **One behavior per scenario** — don't test multiple things
2. **Use tags** — `@functional`, `@kafka`, `@database`, `@smoke`, `@negative`
3. **Background for shared setup** — data loading goes here
4. **Scenarios are independent** — no ordering dependencies
5. **Use placeholders** — `{generated.user.id}`, `{created.order.id}`

### How to Write Step Definitions

```java
// File: functional-tests/src/test/java/.../steps/YourSteps.java

public class YourSteps {
    private final TestContext context;

    public YourSteps() {
        this.context = SharedTestContext.get(); // Access shared state
    }

    @When("I do something with {string}")
    public void doSomething(String param) {
        // 1. Get data from context
        SyntheticDataSet data = context.getCurrentDataSet();

        // 2. Perform action
        Response response = given()
                .contentType(ContentType.JSON)
                .body(JsonUtil.toJson(data.getUser()))
                .post("/api/endpoint");

        // 3. Store result in context for @Then steps
        context.setLastApiResponse(response);
    }

    @Then("the result should be {string}")
    public void verifyResult(String expected) {
        Response response = context.getLastApiResponse();
        assertThat(response.jsonPath().getString("field"))
                .isEqualTo(expected);
    }
}
```

### Step Definition Rules
1. Get shared state from `SharedTestContext.get()`
2. `@When` steps perform actions and store results
3. `@Then` steps only assert — no side effects
4. Keep steps generic and reusable
5. Use `resolvePlaceholders()` for dynamic values

---

## Layer 3: Kafka Event Tests

### How to Write Kafka Feature Scenarios

```gherkin
@kafka
Feature: Your Kafka Tests

  Background:
    Given synthetic test data is loaded for scenario "your-scenario"
    And Kafka consumers are listening on topics:
      | your.topic.name |

  Scenario: Action produces expected event
    When I submit a POST to "/api/trigger"
    Then within 10 seconds a Kafka event should appear on "your.topic.name" with:
      | field      | expected           |
      | eventType  | YOUR_EVENT_TYPE    |
    And the event payload should contain field "importantField"
```

### Kafka Testing Patterns

**Positive assertion (event appears):**
```gherkin
Then within 10 seconds a Kafka event should appear on "topic" with:
  | field     | expected    |
  | eventType | SOME_EVENT  |
```

**Negative assertion (event does NOT appear):**
```gherkin
Then no Kafka event should appear on "topic" within 5 seconds
```

**Event ordering:**
```gherkin
Then the Kafka events on "topic" should appear in order:
  | EVENT_A |
  | EVENT_B |
  | EVENT_C |
```

**Payload deep inspection:**
```gherkin
And the event payload should contain field "customerId" matching the generated user ID
```

### Adding New Event Types
1. Add the event type to `KafkaEventGenerator.fromOrder()`
2. Add the type to the JSON schema in `contract-tests/src/test/resources/schemas/`
3. Write a contract test in `KafkaEventSchemaTest`
4. Write a functional test in a `.feature` file

---

## Layer 4: Database Tests

### How to Write Database Test Scenarios

```gherkin
@database
Feature: Your Database Tests

  Background:
    Given synthetic test data is loaded for scenario "your-scenario"
    And a database connection is established

  Scenario: Data persists correctly
    Given an order has been created via the API for the generated user
    When I query the orders table for the created order ID
    Then the database record should have:
      | column      | expected            |
      | status      | CONFIRMED           |
      | customer_id | {generated.user.id} |
```

### Adding New Database Assertions

```java
// In DatabaseSteps.java:

@When("I query the {word} table for {string}")
public void queryTable(String tableName, String id) {
    DatabaseHelper db = context.getDbHelper();
    Map<String, Object> row = db.queryRow(
        "SELECT * FROM " + tableName + " WHERE id = ?", id
    );
    context.put("db.query.result", row);
}

@Then("the count of {word} records should be {int}")
public void countRecords(String tableName, int expected) {
    DatabaseHelper db = context.getDbHelper();
    long count = db.count(tableName, null);
    assertThat(count).isEqualTo(expected);
}
```

### Database Testing Rules
1. Always use parameterized queries (prevent SQL injection even in tests)
2. Assert on specific columns, not entire rows
3. Test referential integrity (FK relationships)
4. Test timestamps (updated_at > created_at)
5. Test cascade behaviors (cancel order -> cancel shipments)

---

## Layer 5: Contract Tests

### API Contracts (Pact)

```java
// In contract-tests/src/test/java/.../api/

@Pact(consumer = "YourConsumer")
public V4Pact yourInteraction(PactDslWithProvider builder) {
    return builder
        .given("precondition state")
        .uponReceiving("description of interaction")
        .path("/api/endpoint")
        .method("GET")
        .willRespondWith()
        .status(200)
        .body(new PactDslJsonBody()
            .stringType("id")          // Any string (type matching)
            .stringValue("status", "ACTIVE")  // Exact value
            .decimalType("amount")     // Any decimal
        )
        .toPact(V4Pact.class);
}
```

### Kafka Event Contracts (JSON Schema)

```java
// In contract-tests/src/test/java/.../kafka/

@Test
void yourEventConformsToSchema() {
    KafkaEvent event = createYourEvent();
    JsonNode node = JsonUtil.getMapper().valueToTree(event);
    Set<ValidationMessage> errors = yourSchema.validate(node);
    assertThat(errors).isEmpty();
}
```

### Contract Testing Rules
1. Pact tests define the EXPECTED API shape, not the actual
2. Use type matching (`stringType`) over value matching where possible
3. JSON schemas go in `resources/schemas/` with `.schema.json` extension
4. Test both valid AND invalid event structures
5. Schemas are the source of truth for event contracts

---

## Layer 6: Performance Tests (JMeter)

### Running Performance Tests

```bash
# Prerequisite: WireMock must be running
docker compose up -d wiremock
sleep 5

# Run load test from the project root
jmeter -n -t perf-tests/test-plans/order-load-test.jmx \
  -Jhost=localhost -Jport=8080 \
  -Jthreads=10 -Jrampup=5 -Jduration=60 \
  -l results.jtl -e -o report/

# Validate thresholds
python3 perf-tests/scripts/check-perf-thresholds.py results.jtl
```

### Customizing Thresholds

Edit `check-perf-thresholds.py` DEFAULT_THRESHOLDS or pass a JSON config:
```json
{
  "max_avg_response_ms": 500,
  "max_p95_response_ms": 1500,
  "max_error_rate_pct": 2.0,
  "min_throughput_rps": 100,
  "samplers": {
    "Create Order": {
      "max_avg_response_ms": 800
    }
  }
}
```

---

## Configuration

All configuration is environment-variable driven. Set any property via env var:

| Property | Env Variable | Default |
|----------|-------------|---------|
| `app.base.url` | `APP_BASE_URL` | `http://localhost:8080` |
| `kafka.bootstrap.servers` | `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` |
| `db.url` | `DB_URL` | `jdbc:postgresql://localhost:5432/testdb` |
| `use.testcontainers` | `USE_TESTCONTAINERS` | `true` |

## Running in CI/CD (GitHub Actions)

The pipeline is defined in `.github/workflows/test-pipeline.yml`.

### Automatic Triggers
- **Push to main or develop** — runs data generation, functional tests, and contract tests. Performance tests only run on main.
- **Pull request to main** — runs data generation, functional tests, and contract tests.

### Manual Trigger (workflow_dispatch)
Trigger a full pipeline run (including performance tests) from the GitHub UI:
1. Go to your repository on GitHub
2. Click the **Actions** tab
3. Select **"Test Pipeline"** from the left sidebar
4. Click **"Run workflow"** dropdown (top right)
5. Select the branch, optionally set JMeter threads and duration
6. Click the green **"Run workflow"** button

Or via the GitHub CLI:
```bash
gh workflow run test-pipeline.yml -f perf_threads=100 -f perf_duration=600
```

### Pipeline Flow
```
Stage 1: Generate Data ──→ Stage 2a: Functional + Database Tests ──→ Stage 3: Performance
                       ──→ Stage 2b: Contract Tests (parallel)   ──↗
```

### CI vs Local Differences
- **Locally**: `docker compose` starts WireMock, Kafka, and PostgreSQL. You run JMeter from the CLI.
- **In CI**: GitHub Actions service containers provide Kafka and PostgreSQL. A JMeter GitHub Action runs the load test. Test data is shared across jobs via artifacts.

### Viewing CI Results
After a run completes, click on it in the Actions tab. Each job shows logs inline. Scroll to the bottom of the run page to download artifacts: **functional-test-reports**, **pact-contracts**, and **perf-reports**.
