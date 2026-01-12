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
 * - KEEP AS-IS (User requested)
 */
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

/**
 * Compatibility guard for JVM args:
 * -XX:+EnableDynamicAgentLoading is available/meaningful on newer JDKs.
 */
fun dynamicAgentArgs(): List<String> {
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
        // logback 제외하고 log4j2 사용
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-logging")
    }
}

repositories {
    // 사내망/오프라인 환경에서 libs 폴더로 수동 주입 가능
    flatDir { dirs("libs") }

    // 사내 Nexus/Artifactory를 mavenLocal로 미러링하는 경우가 있어 유지
    mavenLocal()

    // 기본 공개 저장소
    mavenCentral()

    // Spring Boot 4.x SNAPSHOT 사용 중이므로 유지
    maven(url = "https://repo.spring.io/snapshot")
}

/**
 * Versions (only where we must pin to fix resolution problems)
 * - Spring/Java versions are kept unchanged.
 * - Testcontainers version pinned via BOM so modules get versions.
 */
val testcontainersBomVersion = "1.21.4" // latest 1.x line (stable) :contentReference[oaicite:3]{index=3}

dependencies {
    // --- Spring Boot starters ---
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

    // --- Google / etc ---
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
    implementation("com.github.ulisesbocchio:jasypt-spring-boot-starter:3.0.5")

    // --- Lombok ---
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // --- Tests ---
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")

    /**
     * ✅ Fix: Testcontainers version resolution
     * - Without a BOM (or explicit versions), Gradle ends up with "org.testcontainers:junit-jupiter:" (blank version)
     * - Pin via BOM so junit-jupiter/mongodb get versions consistently.
     */
    testImplementation(platform("org.testcontainers:testcontainers-bom:$testcontainersBomVersion"))
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:mongodb")

    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // --- Local jars under /libs (manual injection scenario) ---
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

tasks.register("printTestCompileClasspath") {
    group = "Verification"
    description = "Prints the resolved testCompileClasspath entries"
    doLast {
        configurations.testCompileClasspath.get().forEach { println(it) }
    }
}
