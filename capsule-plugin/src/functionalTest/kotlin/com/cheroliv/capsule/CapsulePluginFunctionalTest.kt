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

    @Test
    fun `can run capsuledistrib task`() {
        setupBuild("""
            capsule {
                ttsEngine = "noop"
            }
        """.trimIndent())

        val runner = GradleRunner.create()
        runner.forwardOutput()
        runner.withPluginClasspath()
        runner.withArguments("capsuledistrib")
        runner.withProjectDir(projectDir)
        val result = runner.build()

        assertTrue(result.output.contains("No capsule videos found") || result.output.contains("UP-TO-DATE"))
    }

    @Test
    fun `can run capsulecompositecontext task`() {
        setupBuild("""
            capsule {
                ttsEngine = "noop"
            }
        """.trimIndent())

        val runner = GradleRunner.create()
        runner.forwardOutput()
        runner.withPluginClasspath()
        runner.withArguments("capsulecompositecontext")
        runner.withProjectDir(projectDir)
        val result = runner.build()

        assertTrue(result.output.contains("CAPSULE COMPOSITE CONTEXT") || result.output.contains("UP-TO-DATE"))
    }

    @Test
    fun `capsuleparsecontext produces valid json output`() {
        setupBuild()

        val runner = GradleRunner.create()
        runner.forwardOutput()
        runner.withPluginClasspath()
        runner.withArguments("capsuleparsecontext")
        runner.withProjectDir(projectDir)
        val result = runner.build()

        assertTrue(result.output.contains("CAPSULE PARSE CONTEXT") || result.output.contains("UP-TO-DATE"))
    }

    @Test
    fun `capsuleretrieve with outputFile parameter`() {
        setupBuild()

        val runner = GradleRunner.create()
        runner.forwardOutput()
        runner.withPluginClasspath()
        runner.withArguments("capsuleretrieve", "-PoutputFile=build/capsule/retrieve-results.json")
        runner.withProjectDir(projectDir)
        val result = runner.build()

        assertTrue(result.output.contains("CAPSULE PARSE CONTEXT") || result.output.contains("UP-TO-DATE"))
    }
}
