import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URL

val kotlinVersion: String by project
val ktorVersion: String by project
val kotestVersion: String by project

plugins {
    kotlin("jvm") version "1.4.32"
    id("org.jetbrains.dokka") version "1.4.30"
    signing
    `maven-publish`
    id("io.github.gradle-nexus.publish-plugin") version "1.0.0"
}

group = "com.michaelstrasser"
version = "0.2.13-SNAPSHOT"

repositories {
    mavenCentral()
    // For kotlinx-html used by Dokka
    maven(url = "https://dl.bintray.com/kotlin/kotlinx")
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

    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.kotest:kotest-property:$kotestVersion")
    testImplementation("io.kotest:kotest-extensions-junitxml:$kotestVersion")
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "1.8"
}
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "1.8"
}

tasks.test {
    useJUnitPlatform()
    dependsOn("cleanTest")
    systemProperty("gradle.build.dir", project.buildDir)
}

tasks.withType<KotlinCompile> {
    kotlinOptions.freeCompilerArgs = listOf("-Xuse-experimental=kotlin.Experimental")
}

tasks.withType<DokkaTask>().configureEach {
    dokkaSourceSets {
        named("main") {
            moduleName.set("Ktor features Zipkin")
            sourceLink {
                localDirectory.set(file("src/main/kotlin"))
                remoteUrl.set(
                    URL("https://github.com/mjstrasser/ktor-features-zipkin/tree/main/src/main/kotlin")
                )
                remoteLineSuffix.set("#L")
            }
        }
    }
}

tasks.register<Jar>("sourcesJar") {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

tasks.register<Jar>("dokkaJar") {
    archiveClassifier.set("javadoc")
    from(layout.buildDirectory.dir("dokka/html"))
}

publishing {
    publications {
        create<MavenPublication>("mavenKotlin") {
            from(components["kotlin"])
            artifact(tasks["sourcesJar"])
            artifact(tasks["dokkaJar"])
            pom {
                name.set("ktor-features-zipkin")
                description.set("Ktor feature for OpenZipkin tracing IDs")
                url.set("https://github.com/mjstrasser/ktor-features-zipkin")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("mjstrasser")
                        name.set("Michael Strasser")
                        email.set("code@michaelstrasser.com")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/mjstrasser/ktor-features-zipkin.git")
                    url.set("https://github.com/mjstrasser/ktor-features-zipkin")
                }
            }
        }
    }
}

nexusPublishing {
    val ossrhUsername: String? by project
    val ossrhPassword: String? by project
    repositories {
        create("sonatype") {
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
            username.set(ossrhUsername)
            password.set(ossrhPassword)
        }
    }
}

signing {
    val signingKeyId: String? by project
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
    sign(publishing.publications["mavenKotlin"])
}
