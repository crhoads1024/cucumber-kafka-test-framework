package com.enterprise.testing.datagen.generators;

import com.enterprise.testing.shared.model.*;
import net.datafaker.Faker;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates Orders correlated to a given User.
 * 
 * Order characteristics are influenced by customer tier:
 * - VIP customers get larger orders with premium products
 * - BASIC customers get smaller, simpler orders
 * This makes the synthetic data realistic for testing business logic.
 */
public class OrderGenerator {

    private final Faker faker;
    private final Random random;

    // Product catalog for generating realistic items
    private static final String[][] PRODUCTS = {
            {"PROD-001", "Wireless Headphones", "79.99"},
            {"PROD-002", "USB-C Hub", "49.99"},
            {"PROD-003", "Mechanical Keyboard", "129.99"},
            {"PROD-004", "4K Monitor", "399.99"},
            {"PROD-005", "Laptop Stand", "34.99"},
            {"PROD-006", "Webcam HD", "69.99"},
            {"PROD-007", "Bluetooth Speaker", "59.99"},
            {"PROD-008", "Wireless Mouse", "29.99"},
            {"PROD-009", "External SSD 1TB", "89.99"},
            {"PROD-010", "Docking Station", "199.99"},
    };

    public OrderGenerator() {
        this.faker = new Faker();
        this.random = new Random();
    }

    public OrderGenerator(long seed) {
        this.faker = new Faker(new Random(seed));
        this.random = new Random(seed);
    }

    /**
     * Generate orders for a specific user. The number and size of orders
     * is influenced by the user's customer tier.
     */
    public List<Order> generateForUser(User user, int count) {
        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            orders.add(generateSingleOrder(user));
        }
        return orders;
    }

    /**
     * Generate a single order for a user.
     */
    public Order generateSingleOrder(User user) {
        Order order = new Order();
        order.setCustomerId(user.getId());

        int itemCount = getItemCountForTier(user.getTier());
        for (int i = 0; i < itemCount; i++) {
            order.addItem(generateItem(user.getTier()));
        }

        // Set status based on probability
        order.setStatus(randomStatus());
        return order;
    }

    private OrderItem generateItem(CustomerTier tier) {
        String[] product = PRODUCTS[random.nextInt(PRODUCTS.length)];
        int maxQty = tier == CustomerTier.VIP ? 5 : (tier == CustomerTier.PREMIUM ? 3 : 2);
        int quantity = 1 + random.nextInt(maxQty);

        BigDecimal basePrice = new BigDecimal(product[2]);
        // VIP customers might get premium pricing or bundles
        if (tier == CustomerTier.VIP && random.nextInt(100) < 30) {
            basePrice = basePrice.multiply(BigDecimal.valueOf(1.2)).setScale(2, RoundingMode.HALF_UP);
        }

        return new OrderItem(product[0], product[1], quantity, basePrice);
    }

    private int getItemCountForTier(CustomerTier tier) {
        return switch (tier) {
            case VIP -> 2 + random.nextInt(4);      // 2-5 items
            case PREMIUM -> 1 + random.nextInt(3);   // 1-3 items
            case BASIC -> 1 + random.nextInt(2);     // 1-2 items
        };
    }

    private OrderStatus randomStatus() {
        int roll = random.nextInt(100);
        if (roll < 40) return OrderStatus.CONFIRMED;
        if (roll < 60) return OrderStatus.PROCESSING;
        if (roll < 75) return OrderStatus.SHIPPED;
        if (roll < 85) return OrderStatus.DELIVERED;
        if (roll < 95) return OrderStatus.PENDING;
        return OrderStatus.CANCELLED;
    }
}
