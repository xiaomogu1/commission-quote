// End-to-end tests for the whole system. Starts ../docker-compose.yml (real Docker images) and calls it
// over HTTP, the way a client would. Needs Docker; tests are skipped if Docker isn't available.
//   ./gradlew test
plugins {
    java
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Same library versions as the applications.
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.6"))
    // Testcontainers 2.0.2+ is needed for Docker Engine 29, which rejects the old API version 1.x requests.
    testImplementation(platform("org.testcontainers:testcontainers-bom:2.0.2"))
    testImplementation("org.springframework:spring-web")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testImplementation("com.jayway.jsonpath:json-path")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.slf4j:slf4j-simple")
}

tasks.test {
    useJUnitPlatform()
}
