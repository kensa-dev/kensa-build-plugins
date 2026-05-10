package dev.kensa.gradle

import io.kotest.matchers.shouldBe
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class PublishedPluginSmokeTest {

    private val pluginRepo: String = System.getProperty("smoke.test.repo")
        ?: error("System property 'smoke.test.repo' not set; configure it on the smokeTest task.")
    private val pluginVersion: String = System.getProperty("smoke.test.plugin.version")
        ?: error("System property 'smoke.test.plugin.version' not set; configure it on the smokeTest task.")

    @Test
    fun `consumer resolves the published plugin and all transitive dependencies`(@TempDir projectDir: Path) {
        Files.writeString(
            projectDir.resolve("settings.gradle.kts"),
            """
            pluginManagement {
                repositories {
                    maven { url = uri("$pluginRepo") }
                    gradlePluginPortal()
                }
            }
            rootProject.name = "smoke-consumer"
            """.trimIndent()
        )

        Files.writeString(
            projectDir.resolve("build.gradle.kts"),
            """
            plugins {
                id("dev.kensa.gradle-plugin") version "$pluginVersion"
                id("org.jetbrains.kotlin.jvm") version "2.3.21"
            }
            """.trimIndent()
        )

        // Buildscript classpath is resolved before any task runs — `help` is enough to
        // trigger the same "Could not find dev.kensa:site-common" failure clearwave saw.
        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments("help", "--refresh-dependencies", "--stacktrace")
            .build()

        result.task(":help")?.outcome shouldBe TaskOutcome.SUCCESS
    }
}
