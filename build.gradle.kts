import org.gradle.api.JavaVersion
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.internal.jvm.Jvm

plugins {
    id("java")
    id("org.springframework.boot") version "4.0.0-SNAPSHOT"
    id("io.spring.dependency-management") version "1.1.7"
}

/**
 * Offline mode toggle
 * - ./gradlew test --offline
 * - GRADLE_FORCE_OFFLINE=true ./gradlew test
 * - ./gradlew -PforceOffline test
 */
val gradleForceOffline: Boolean =
    project.hasProperty("forceOffline") || System.getenv("GRADLE_FORCE_OFFLINE") == "true"

if (gradleForceOffline && !gradle.startParameter.isOffline) {
    gradle.startParameter.isOffline = true
    logger.lifecycle("Gradle offline mode enabled by GRADLE_FORCE_OFFLINE env or -PforceOffline")
}

group = "com.example"
version = "0.0.1-SNAPSHOT"
description = "AI 독후감 관리 서비스"

/**
 * Java Toolchain (compile/runtime for tasks)
 * - Using toolchain avoids “works on my machine” issues.
 * - Keep Gradle JVM separate; IDE/CI should point Gradle JVM to JDK 25 if possible.
 */
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

/**
 * Ensure bytecode + standard library linkage matches Java 25 explicitly.
 * (More reliable than source/targetCompatibility.)
 */
tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
}

/**
 * Compatibility guard for JVM args:
 * -XX:+EnableDynamicAgentLoading is available/meaningful on newer JDKs.
 * Guarding prevents failures if build runs with older JVMs in some environments.
 */
fun dynamicAgentArgs(): List<String> {
    // ✅ FIX: Jvm.current().javaVersion is nullable on some Gradle/Kotlin DSL combos
    val major: Int = (Jvm.current().javaVersion ?: JavaVersion.current())
        .majorVersion
        .toInt()

    return if (major >= 21) listOf("-XX:+EnableDynamicAgentLoading") else emptyList()
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
    all {
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-logging")
    }
}

repositories {
    // Scenario 2: local jars under /libs
    flatDir { dirs("libs") }

    // Scenario 3: local maven repo first
    mavenLocal()

    // Spring repos for snapshot/release
    maven(url = "https://repo.spring.io/release")
    maven(url = "https://repo.spring.io/snapshot")

    mavenCentral()
}

dependencies {
    // Spring Boot 4 SNAPSHOT BOM
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.0.0-SNAPSHOT"))

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-log4j2")
    implementation("org.springframework:spring-aop")
    implementation("org.aspectj:aspectjrt:1.9.25")
    implementation("org.aspectj:aspectjweaver:1.9.25")

    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")

    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.thymeleaf.extras:thymeleaf-extras-springsecurity6")

    implementation("org.springframework.boot:spring-boot-starter-actuator")

    implementation("com.google.api-client:google-api-client:1.32.1")
    implementation("com.google.oauth-client:google-oauth-client:1.36.0")
    implementation("com.google.oauth-client:google-oauth-client-jetty:1.36.0")
    implementation("com.google.http-client:google-http-client-gson:1.45.0")
    implementation("com.google.apis:google-api-services-drive:v3-rev197-1.25.0")
    implementation("com.google.auth:google-auth-library-oauth2-http:1.29.0")

    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("com.google.code.gson:gson:2.11.0")

    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("io.github.cdimascio:java-dotenv:5.2.2")
    implementation("org.jsoup:jsoup:1.18.3")
    implementation("jakarta.validation:jakarta.validation-api:3.1.0")

    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.79")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    developmentOnly("org.springframework.boot:spring-boot-devtools")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.19.0"))
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:mongodb")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Scenario 2: pick up any jars placed in /libs
    implementation(fileTree("libs") { include("*.jar") })
    testImplementation(fileTree("libs") { include("*.jar") })
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaExec>().configureEach {
    jvmArgs(dynamicAgentArgs())
}

tasks.withType<Test>().configureEach {
    jvmArgs(dynamicAgentArgs())
}
