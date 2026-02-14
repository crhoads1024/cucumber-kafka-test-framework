package com.enterprise.testing.datagen;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * CLI entry point for the data generation pipeline stage.
 * 
 * Run in CI/CD as:
 *   java -jar data-generator.jar [--output-dir ./generated-data] [--profile ci]
 * 
 * Or via Maven:
 *   mvn exec:java -Dexec.mainClass="com.enterprise.testing.datagen.DataGeneratorMain"
 */
public class DataGeneratorMain {

    private static final Logger log = LoggerFactory.getLogger(DataGeneratorMain.class);

    public static void main(String[] args) {
        log.info("=== Enterprise Test Data Generator ===");

        String outputDir = parseArg(args, "--output-dir", "./generated-data");
        String profile = parseArg(args, "--profile", "default");

        TestDataRegistry registry = TestDataRegistry.getInstance();

        log.info("Generating data for profile: {}", profile);

        // Define test scenarios based on profile
        switch (profile) {
            case "ci" -> generateCiProfile(registry);
            case "load" -> generateLoadProfile(registry);
            default -> generateDefaultProfile(registry);
        }

        registry.persistToDisk(outputDir);
        log.info("Data generation complete. Output: {}", outputDir);
    }

    /**
     * Default profile: small dataset for local development.
     */
    private static void generateDefaultProfile(TestDataRegistry registry) {
        // E-commerce scenarios
        registry.generateScenario("order-happy-path", 1, 2);
        registry.generateScenario("order-cancellation", 1, 1);
        registry.generateScenario("vip-customer-flow", 1, 3);
        registry.generateScenario("multi-user-batch", 5, 1);

        // Trade/settlement scenarios (fetches real market data)
        registry.generateTradeScenario("trade-happy-path",
                List.of("AAPL", "BTC-USD"), 2);
        registry.generateTradeScenario("trade-crypto-batch",
                List.of("BTC-USD", "XRP-USD", "ADA-USD"), 2);
        registry.generateRoundTripScenario("trade-round-trip", "TSLA");
    }

    /**
     * CI profile: moderate dataset for pipeline testing.
     */
    private static void generateCiProfile(TestDataRegistry registry) {
        // E-commerce scenarios
        registry.generateScenario("order-happy-path", 3, 3);
        registry.generateScenario("order-cancellation", 2, 1);
        registry.generateScenario("order-edge-cases", 1, 5);
        registry.generateScenario("vip-customer-flow", 2, 3);
        registry.generateScenario("multi-user-batch", 10, 2);
        registry.generateScenario("perf-baseline", 50, 2);

        // Trade/settlement scenarios
        registry.generateTradeScenario("trade-happy-path",
                List.of("AAPL", "MSFT", "GOOGL", "BTC-USD", "XRP-USD"), 3);
        registry.generateTradeScenario("trade-crypto-batch",
                List.of("BTC-USD", "XRP-USD", "CRV-USD", "LINK-USD", "ADA-USD"), 5);
        registry.generateTradeScenario("trade-settlement-failures",
                List.of("TSLA", "JPM"), 10);
        registry.generateRoundTripScenario("trade-round-trip-equity", "AAPL");
        registry.generateRoundTripScenario("trade-round-trip-crypto", "BTC-USD");
    }

    /**
     * Load profile: large dataset for performance testing.
     */
    private static void generateLoadProfile(TestDataRegistry registry) {
        registry.generateScenario("perf-load-test", 200, 5);
        registry.generateScenario("perf-spike-test", 500, 1);
    }

    private static String parseArg(String[] args, String flag, String defaultValue) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(flag)) {
                return args[i + 1];
            }
        }
        return defaultValue;
    }
}
