plugins {
    kotlin("jvm") version "2.0.0"
    kotlin("plugin.serialization") version "2.0.0"
    id("org.jetbrains.kotlinx.kover") version "0.8.3"
    id("info.solidsoft.pitest") version "1.15.0"
    application
}

group = "com.hotel.contacthealth"
version = "0.0.1"

repositories {
    mavenCentral()
}

val ktorVersion = "2.3.12"
val logbackVersion = "1.5.6"

dependencies {
    // Ktor Server Core & Engine (Netty)
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")

    // Content Negotiation & kotlinx.serialization (JSON)
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages-jvm:$ktorVersion")

    // Logging (Logback)
    implementation("ch.qos.logback:logback-classic:$logbackVersion")

    // Testes
    testImplementation("io.ktor:ktor-server-tests-jvm:$ktorVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("io.kotest:kotest-property-jvm:5.9.1")
    testImplementation("com.code-intelligence:jazzer-junit:0.22.1")
}

application {
    mainClass.set("com.hotel.contacthealth.ApplicationKt")
}

tasks.test {
    useJUnitPlatform()
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
                    minBound(85)
                }
            }
        }
    }
}

configure<info.solidsoft.gradle.pitest.PitestPluginExtension> {
    junit5PluginVersion.set("1.2.1")
    targetClasses.set(listOf("com.hotel.contacthealth.domain.*"))
    targetTests.set(listOf("com.hotel.contacthealth.*Test"))
    threads.set(4)
    outputFormats.set(listOf("XML", "HTML"))
    timestampedReports.set(false)
}

