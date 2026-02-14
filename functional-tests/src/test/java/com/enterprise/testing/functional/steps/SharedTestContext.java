package com.enterprise.testing.functional.steps;

/**
 * Holds a per-thread TestContext instance so multiple step definition
 * classes can share state within a single Cucumber scenario.
 * 
 * Cucumber creates separate instances of each step definition class,
 * but they all need to access the same TestContext. This thread-local
 * holder solves that without requiring a DI framework.
 * 
 * NOTE: If you later add cucumber-spring or cucumber-picocontainer,
 * you can replace this with proper DI. This works great for now.
 */
public class SharedTestContext {

    private static final ThreadLocal<TestContext> CONTEXT = ThreadLocal.withInitial(TestContext::new);

    public static TestContext get() {
        return CONTEXT.get();
    }

    public static void reset() {
        CONTEXT.remove();
    }
}
