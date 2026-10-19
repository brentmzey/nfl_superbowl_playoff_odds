import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

plugins {
    kotlin("multiplatform") version "2.1.10"
    kotlin("plugin.serialization") version "2.1.10"
    jacoco
}

group = "com.gridiron.superbowl"
version = "0.1.0-RC1"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)

    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            implementation(kotlin("test-junit5"))
        }
    }
}

val jvmTarget = kotlin.targets.getByName("jvm") as KotlinJvmTarget
val jvmMain = jvmTarget.compilations.getByName("main")

tasks.register<JavaExec>("run") {
    group = "application"
    description = "Fetches live ESPN results, simulates the season + playoffs, and refreshes charts and README."
    mainClass.set("com.gridiron.superbowl.app.MainKt")
    classpath = jvmMain.runtimeDependencyFiles + jvmMain.output.allOutputs
    workingDir = rootDir
    args = (project.findProperty("simArgs") as String?)?.split(" ")?.filter { it.isNotBlank() } ?: emptyList()
}

tasks.register<JacocoReport>("jacocoTestReport") {
    group = "verification"
    description = "JaCoCo coverage for common + JVM code."
    dependsOn("jvmTest")
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    classDirectories.setFrom(jvmMain.output.classesDirs)
    sourceDirectories.setFrom(files("src/commonMain/kotlin", "src/jvmMain/kotlin"))
    executionData.setFrom(fileTree(layout.buildDirectory.dir("jacoco")).matching { include("*.exec") })
}

tasks.register<JacocoCoverageVerification>("jacocoCoverageVerification") {
    group = "verification"
    dependsOn("jvmTest")
    classDirectories.setFrom(
        jvmMain.output.classesDirs.asFileTree.matching { exclude("**/app/MainKt*") }
    )
    executionData.setFrom(fileTree(layout.buildDirectory.dir("jacoco")).matching { include("*.exec") })
    violationRules {
        rule {
            limit { counter = "LINE"; minimum = "0.90".toBigDecimal() }
            limit { counter = "BRANCH"; minimum = "0.90".toBigDecimal() }
        }
    }
}

tasks.named("check") { dependsOn("jacocoCoverageVerification") }
