package com.enterprise.testing.datagen.generators;

import com.enterprise.testing.shared.model.Address;
import com.enterprise.testing.shared.model.CustomerTier;
import com.enterprise.testing.shared.model.User;
import net.datafaker.Faker;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates synthetic User data with realistic, correlated fields.
 * 
 * LESSON: When writing generators, make fields internally consistent.
 * A VIP customer should have older account age, a realistic email format, etc.
 */
public class UserGenerator {

    private final Faker faker;
    private final Random random;

    public UserGenerator() {
        this.faker = new Faker();
        this.random = new Random();
    }

    public UserGenerator(long seed) {
        this.faker = new Faker(new Random(seed));
        this.random = new Random(seed);
    }

    /**
     * Generate a single user with a specific tier.
     */
    public User generate(CustomerTier tier) {
        User user = new User();

        String firstName = faker.name().firstName();
        String lastName = faker.name().lastName();

        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(generateEmail(firstName, lastName));
        user.setPhone(faker.phoneNumber().cellPhone());
        user.setDateOfBirth(generateDob());
        user.setAddress(generateAddress());
        user.setTier(tier);

        return user;
    }

    /**
     * Generate a user with random tier distribution:
     * 60% BASIC, 30% PREMIUM, 10% VIP
     */
    public User generate() {
        int roll = random.nextInt(100);
        CustomerTier tier;
        if (roll < 60) {
            tier = CustomerTier.BASIC;
        } else if (roll < 90) {
            tier = CustomerTier.PREMIUM;
        } else {
            tier = CustomerTier.VIP;
        }
        return generate(tier);
    }

    /**
     * Generate a batch of users.
     */
    public List<User> generateBatch(int count) {
        List<User> users = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            users.add(generate());
        }
        return users;
    }

    private String generateEmail(String firstName, String lastName) {
        String[] domains = {"gmail.com", "yahoo.com", "outlook.com", "protonmail.com", "testmail.com"};
        String domain = domains[random.nextInt(domains.length)];
        String separator = random.nextBoolean() ? "." : "";
        String suffix = String.valueOf(random.nextInt(999));
        return (firstName.toLowerCase() + separator + lastName.toLowerCase() + suffix + "@" + domain)
                .replaceAll("[^a-zA-Z0-9@.]", "");
    }

    private LocalDate generateDob() {
        // Generate ages between 18 and 75
        int age = 18 + random.nextInt(57);
        return LocalDate.now().minusYears(age).minusDays(random.nextInt(365));
    }

    private Address generateAddress() {
        return new Address(
                faker.address().streetAddress(),
                faker.address().city(),
                faker.address().stateAbbr(),
                faker.address().zipCode(),
                "US"
        );
    }
}
