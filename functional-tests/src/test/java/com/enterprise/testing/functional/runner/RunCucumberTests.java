package com.enterprise.testing.functional.runner;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.*;

/**
 * JUnit Platform Suite that discovers and runs Cucumber features.
 * 
 * Run all tests:
 *   mvn test
 * 
 * Run by tag:
 *   mvn test -Dcucumber.filter.tags="@functional"
 *   mvn test -Dcucumber.filter.tags="@kafka"
 *   mvn test -Dcucumber.filter.tags="@database"
 *   mvn test -Dcucumber.filter.tags="@smoke"
 */
@Suite
@IncludeEngines("cucumber")
@SelectPackages("com.enterprise.testing.functional")
@ConfigurationParameter(key = FEATURES_PROPERTY_NAME, value = "src/test/resources/features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.enterprise.testing.functional.steps")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty, json:target/cucumber-reports/cucumber.json, html:target/cucumber-reports/cucumber.html")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "not @wip")
public class RunCucumberTests {
    // This class is a configuration holder only.
    // Cucumber discovers features and glue code via the annotations above.
}
