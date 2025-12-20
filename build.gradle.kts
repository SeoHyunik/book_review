plugins {
    id("java")
    id("org.springframework.boot") version "4.0.0-SNAPSHOT"
    id("io.spring.dependency-management") version "1.1.7"
}

val gradleForceOffline: Boolean = project.hasProperty("forceOffline") || System.getenv("GRADLE_FORCE_OFFLINE") == "true"

if (gradleForceOffline && !gradle.startParameter.isOffline) {
    gradle.startParameter.isOffline = true
    logger.lifecycle("Gradle offline mode enabled by GRADLE_FORCE_OFFLINE env or -PforceOffline")
}

group = "com.example"
version = "0.0.1-SNAPSHOT"
description = "AI 기반 독후감 관리 서비스"

java {
    toolchain {
        // Use the locally available JDK to avoid remote downloads in offline environments.
        languageVersion.set(JavaLanguageVersion.of(21))
    }
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
    flatDir { dirs("libs") }
    mavenLocal()
    maven(url = "https://repo.spring.io/release")
    maven(url = "https://repo.spring.io/snapshot")
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("org.springframework.boot:spring-boot-starter-log4j2")

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

    implementation(fileTree("libs") { include("*.jar") })
    testImplementation(fileTree("libs") { include("*.jar") })
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaExec>().configureEach {
    jvmArgs("-XX:+EnableDynamicAgentLoading")
}

tasks.withType<Test>().configureEach {
    jvmArgs("-XX:+EnableDynamicAgentLoading")
}
