import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    repositories {
        jcenter()
    }
}

val kotlinVersion: String by project
val ktorVersion: String by project
val junit5Version: String by project
val spekVersion: String by project
val assertkVersion: String by project

plugins {
    kotlin("jvm") version "1.4.32"
    `maven-publish`
    id("com.jfrog.bintray") version "1.8.5"
}

group = "com.michaelstrasser"
version = "0.2.10"

repositories {
    jcenter()
    maven("https://dl.bintray.com/kotlin/ktor")
}

dependencies {
    implementation(group = "org.jetbrains.kotlin", name = "kotlin-stdlib-jdk8", version = kotlinVersion)

    implementation(group = "io.ktor", name = "ktor-server-core", version = ktorVersion)
    implementation(group = "io.ktor", name = "ktor-client-core", version = ktorVersion)
    implementation(group = "io.ktor", name = "ktor-client-core-jvm", version = ktorVersion)

    testImplementation(group = "io.ktor", name = "ktor-client-mock", version = ktorVersion)
    testImplementation(group = "io.ktor", name = "ktor-client-mock-jvm", version = ktorVersion)
    testImplementation(group = "io.ktor", name = "ktor-server-tests", version = ktorVersion)
    testImplementation(group = "io.ktor", name = "ktor-client-tests", version = ktorVersion)

    testImplementation(group = "org.junit.jupiter", name = "junit-jupiter-api", version = junit5Version)
    testRuntimeOnly(group = "org.junit.jupiter", name = "junit-jupiter-engine", version = junit5Version)

    testImplementation(group = "com.willowtreeapps.assertk", name = "assertk-jvm", version = assertkVersion)

    // Override the version in Spek.
    testImplementation(kotlin(module = "reflect", version = kotlinVersion))
    testImplementation(group = "org.spekframework.spek2", name = "spek-dsl-jvm", version = spekVersion)
    testRuntimeOnly(group = "org.spekframework.spek2", name = "spek-runner-junit5", version = spekVersion)
}

tasks.test {
    useJUnitPlatform()
    dependsOn("cleanTest")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.freeCompilerArgs = listOf("-Xuse-experimental=kotlin.Experimental")
}

tasks.register<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

publishing {
    publications {
        create<MavenPublication>("mavenKotlin") {
            from(components["kotlin"])
            artifact(tasks["sourcesJar"])
        }
    }
    repositories {
        maven {
            name = "buildDirRepo"
            url = uri("file://${buildDir}/repo")
        }
    }
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "1.8"
}
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "1.8"
}