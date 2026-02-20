plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    application
}

group = "com.polecatworks.kotlin.k8smicro"
version = "2.0.0-SNAPSHOT"

application {
    mainClass.set("com.polecatworks.kotlin.k8smicro.K8sMicroCliKt")
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://packages.confluent.io/maven/")
    }
}

val ktorVersion = "3.2.3"
val kafkaVersion = "3.4.0"
val serializationVersion = "1.9.0"
val logbackVersion = "1.4.12"
val mockkVersion = "1.13.12"
val exposedVersion = "0.41.1"
val prometheusVersion = "1.10.2"
val coroutinesVersion = "1.8.0"

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }

    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation("io.ktor:ktor-server-cio-jvm:$ktorVersion") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation("io.ktor:ktor-server-call-logging-jvm:$ktorVersion") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation("io.ktor:ktor-server-metrics-micrometer-jvm:$ktorVersion") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }

    implementation("io.ktor:ktor-client-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-cio-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-logging-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation-jvm:$ktorVersion")

    implementation("ch.qos.logback:logback-classic:$logbackVersion") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.4")

    implementation("io.micrometer:micrometer-registry-prometheus:$prometheusVersion")

    implementation("com.github.ajalt:clikt:2.8.0")

    implementation("org.apache.kafka:kafka-clients:$kafkaVersion")
    implementation("org.apache.kafka:kafka-streams:$kafkaVersion")
    implementation("io.confluent:kafka-streams-avro-serde:7.4.0")

    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.postgresql:postgresql:42.5.5")

    runtimeOnly("com.sksamuel.hoplite:hoplite-yaml:2.7.0")
    implementation("com.sksamuel.hoplite:hoplite-core:2.7.0")

    implementation("com.github.avro-kotlin.avro4k:avro4k-core:2.3.0")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.junit.vintage:junit-vintage-engine:5.8.2")

    testImplementation("io.mockk:mockk-jvm:$mockkVersion") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    testImplementation("io.ktor:ktor-server-test-host-jvm:$ktorVersion") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    testImplementation("io.ktor:ktor-client-mock-jvm:$ktorVersion")
    testImplementation("org.apache.kafka:kafka-streams-test-utils:$kafkaVersion")
}

kotlin {
    jvmToolchain(21)
}

tasks.jar {
    manifest {
        attributes("Main-Class" to "com.polecatworks.kotlin.k8smicro.K8sMicroCliKt")
    }
}

tasks.shadowJar {
    archiveClassifier.set("jar-with-dependencies")
    mergeServiceFiles()
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("-Dnet.bytebuddy.experimental=true")
}
