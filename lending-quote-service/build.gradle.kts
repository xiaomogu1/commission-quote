plugins {
    java
    id("org.springframework.boot") version "3.5.6"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.bendigo"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.mapstruct:mapstruct:1.6.3")
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.3.0")
    implementation("org.springframework.boot:spring-boot-starter-aop")

    // Order matters: Lombok must run before MapStruct so MapStruct can see the generated getters/constructors.
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok-mapstruct-binding:0.2.0")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.wiremock:wiremock-standalone:3.13.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Test levels, one folder each under src/test, run in this order (fast feedback first):
//   src/test/unit       ./gradlew unitTest       no Spring context
//   src/test/component  ./gradlew componentTest  @SpringBootTest, vendor replaced by WireMock or a mock
//   src/test/blackbox   ./gradlew blackBoxTest   app on a real port, called over HTTP like the browser does
// ./gradlew test (and check/build) runs all three in that order, stopping at the first level that fails.
// End-to-end tests of the whole system live in ../e2e-tests.
val testLevels = mapOf(
    "unitTest" to "unit",
    "componentTest" to "component",
    "blackBoxTest" to "blackbox",
)

sourceSets {
    // Gradle's default test source set is left empty: each level has its own source set instead.
    test {
        java.setSrcDirs(emptyList<String>())
        resources.setSrcDirs(emptyList<String>())
    }
    testLevels.forEach { (name, folder) ->
        create(name) {
            java.setSrcDirs(listOf("src/test/$folder/java"))
            resources.setSrcDirs(listOf("src/test/$folder/resources"))
            compileClasspath += sourceSets.main.get().output
            runtimeClasspath += sourceSets.main.get().output
        }
    }
}

// Every level uses the same test libraries (Spring Boot test, Mockito, AssertJ, WireMock).
testLevels.keys.forEach { name ->
    configurations["${name}Implementation"].extendsFrom(configurations.testImplementation.get())
    configurations["${name}RuntimeOnly"].extendsFrom(configurations.testRuntimeOnly.get())
}

val unitTest by tasks.registering(Test::class) {
    description = "Runs unit tests (no Spring context)."
    group = "verification"
    testClassesDirs = sourceSets["unitTest"].output.classesDirs
    classpath = sourceSets["unitTest"].runtimeClasspath
    useJUnitPlatform()
}

val componentTest by tasks.registering(Test::class) {
    description = "Runs component tests (@SpringBootTest)."
    group = "verification"
    testClassesDirs = sourceSets["componentTest"].output.classesDirs
    classpath = sourceSets["componentTest"].runtimeClasspath
    useJUnitPlatform()
    mustRunAfter(unitTest)
}

val blackBoxTest by tasks.registering(Test::class) {
    description = "Runs black-box API tests against the app on a real port."
    group = "verification"
    testClassesDirs = sourceSets["blackBoxTest"].output.classesDirs
    classpath = sourceSets["blackBoxTest"].runtimeClasspath
    useJUnitPlatform()
    mustRunAfter(componentTest)
}

tasks.test {
    dependsOn(unitTest, componentTest, blackBoxTest)
}

// Coverage report and the 95% gate, see gradle/coverage.gradle.
apply(from = "gradle/coverage.gradle")
