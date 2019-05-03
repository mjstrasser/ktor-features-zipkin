import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

buildscript {
    repositories {
        jcenter()
    }
}

plugins {
    kotlin("jvm") version "1.3.31"
    maven
}

group = "com.michaelstrasser"
version = "0.1.4"

sourceSets.main {
    withConvention(KotlinSourceSet::class) {
        kotlin.srcDirs("src")
    }
}
sourceSets.test {
    withConvention(KotlinSourceSet::class) {
        kotlin.srcDirs("test")
    }
}

repositories {
    jcenter()
    maven("https://dl.bintray.com/kotlin/ktor")
}

val ktorVersion = "1.1.4"
val junit5Version = "5.4.0"

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.3.31")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-client-mock:$ktorVersion")
    implementation("io.ktor:ktor-client-mock-jvm:$ktorVersion")

    testImplementation("org.junit.jupiter:junit-jupiter-api:$junit5Version")
    testRuntime("org.junit.jupiter:junit-jupiter-engine:$junit5Version")
    testImplementation("io.ktor:ktor-server-tests:$ktorVersion")
    testImplementation("com.willowtreeapps.assertk:assertk-jvm:0.13")
}

tasks.test {
    useJUnitPlatform()
    dependsOn("cleanTest")
}
