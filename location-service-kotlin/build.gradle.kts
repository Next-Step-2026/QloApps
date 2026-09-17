plugins {
    kotlin("jvm") version "1.9.24"
    kotlin("plugin.serialization") version "1.9.24"
    id("org.jetbrains.kotlinx.kover") version "0.8.3"
    id("info.solidsoft.pitest") version "1.15.0"
    application
}

group = "com.hotel.location"
version = "1.0.0"

repositories {
    mavenCentral()
}

val ktorVersion = "2.3.12"

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages-jvm:$ktorVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("io.ktor:ktor-server-tests-jvm:$ktorVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:1.9.24")

    // REST Assured & JSON Schema Validation
    testImplementation("io.rest-assured:rest-assured:5.4.0")
    testImplementation("io.rest-assured:json-schema-validator:5.4.0")

    // Property-Based Testing
    testImplementation("io.kotest:kotest-property-jvm:5.9.1")

    // Fuzz Testing (Jazzer)
    testImplementation("com.code-intelligence:jazzer-junit:0.22.1")
}

application {
    mainClass.set("com.hotel.location.ApplicationKt")
}

tasks.test {
    useJUnitPlatform()
    exclude("**/*FuzzTest*")
    testLogging {
        events("passed", "skipped", "failed")
    }
}

val fuzzTest by tasks.registering(Test::class) {
    description = "Executa testes de Fuzzing guiados com Jazzer"
    group = "verification"
    useJUnitPlatform()
    include("**/*FuzzTest*")
    testLogging {
        events("passed", "skipped", "failed")
    }
}

kover {
    reports {
        total {
            xml {
                onCheck = true
            }
            html {
                onCheck = true
            }
            verify {
                rule {
                    minBound(80)
                }
            }
        }
    }
}

configure<info.solidsoft.gradle.pitest.PitestPluginExtension> {
    junit5PluginVersion.set("1.2.1")
    targetClasses.set(listOf("com.hotel.location.service.*", "com.hotel.location.model.*"))
    targetTests.set(listOf("com.hotel.location.HaversineEngineTest", "com.hotel.location.property.*"))
    threads.set(4)
    outputFormats.set(listOf("XML", "HTML"))
    timestampedReports.set(false)
}

