package com.cheroliv.capsule

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.io.TempDir

class CapsulePluginFunctionalTest {

    @field:TempDir
    lateinit var projectDir: File

    private val buildFile by lazy { projectDir.resolve("build.gradle") }
    private val settingsFile by lazy { projectDir.resolve("settings.gradle") }

    private fun setupBuild(extraConfig: String = "") {
        settingsFile.writeText("")
        buildFile.writeText("""
            plugins {
                id('com.cheroliv.capsule')
            }
            $extraConfig
        """.trimIndent())
    }

    @Test
    fun `can run capsulescript task`() {
        setupBuild()

        val runner = GradleRunner.create()
        runner.forwardOutput()
        runner.withPluginClasspath()
        runner.withArguments("capsulescript")
        runner.withProjectDir(projectDir)
        val result = runner.build()

        assertTrue(result.output.contains("No *-script.txt files found"))
    }

    @Test
    fun `capsulebuild task depends on capsulescript`() {
        setupBuild()

        val runner = GradleRunner.create()
        runner.forwardOutput()
        runner.withPluginClasspath()
        runner.withArguments("capsulebuild")
        runner.withProjectDir(projectDir)
        val result = runner.build()

        assertTrue(result.output.contains("capsulescript") || result.output.contains("No capsule scripts"))
    }

    @Test
    fun `capsulebuild with noop engine falls back gracefully`() {
        setupBuild("""
            capsule {
                ttsEngine = "noop"
            }
        """.trimIndent())

        val runner = GradleRunner.create()
        runner.forwardOutput()
        runner.withPluginClasspath()
        runner.withArguments("capsulebuild")
        runner.withProjectDir(projectDir)
        val result = runner.build()

        assertTrue(result.output.contains("No capsule scripts"))
    }

    @Test
    fun `can run capsulevideo task`() {
        setupBuild("""
            capsule {
                ttsEngine = "noop"
            }
        """.trimIndent())

        val runner = GradleRunner.create()
        runner.forwardOutput()
        runner.withPluginClasspath()
        runner.withArguments("capsulevideo")
        runner.withProjectDir(projectDir)
        val result = runner.build()

        assertTrue(result.output.contains("No *-deck.html files found") || result.output.contains("No *-script.txt files found"))
    }
}
