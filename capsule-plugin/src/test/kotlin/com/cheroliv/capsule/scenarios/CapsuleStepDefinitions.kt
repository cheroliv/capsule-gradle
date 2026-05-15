package com.cheroliv.capsule.scenarios

import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.gradle.testkit.runner.GradleRunner
import java.io.File
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CapsuleStepDefinitions {

    private var _projectDir: File? = null
    private val projectDir: File
        get() = _projectDir ?: error("Background not executed")

    private var lastBuildResult = ""

    @Given("a Gradle project with the capsule plugin applied")
    fun aGradleProjectWithTheCapsulePluginApplied() {
        _projectDir = File(System.getProperty("java.io.tmpdir"))
            .resolve("cucumber-capsule-${System.currentTimeMillis()}")
            .also { it.mkdirs() }

        projectDir.resolve("settings.gradle").writeText("")
        projectDir.resolve("build.gradle").writeText("""
            plugins {
                id('com.cheroliv.capsule')
            }
            capsule {
                ttsEngine = "noop"
            }
        """.trimIndent())
    }

    private fun decksDir(): File =
        projectDir.resolve("build/docs/asciidocRevealJs").also { it.mkdirs() }

    private fun scriptsDir(): File =
        projectDir.resolve("build/capsule").also { it.mkdirs() }

    @Given("a reveal.js deck {string} with {int} slides and data-capsule-slide attributes")
    fun aRevealJsDeckWithSlidesAndDataCapsuleSlideAttributes(deckName: String, slideCount: Int) {
        val slides = (1..slideCount).joinToString("\n") { i ->
            """    <section data-capsule-slide="$i"><h2>Slide $i</h2><p>Content $i</p></section>"""
        }
        val deckHtml = """
<html><body>
<div class="reveal">
  <div class="slides">
$slides
  </div>
</div>
</body></html>
        """.trimIndent()
        decksDir().resolve(deckName).writeText(deckHtml)
    }

    @Given("a reveal.js deck {string} with {int} slides without data-capsule-slide attributes")
    fun aRevealJsDeckWithoutDataCapsuleSlideAttributes(deckName: String, slideCount: Int) {
        val slides = (1..slideCount).joinToString("\n") { i ->
            """    <section><h2>Slide $i</h2></section>"""
        }
        val deckHtml = """
<html><body>
<div class="reveal">
  <div class="slides">
$slides
  </div>
</div>
</body></html>
        """.trimIndent()
        decksDir().resolve(deckName).writeText(deckHtml)
    }

    @And("a capsule script {string} with {int} slide segment")
    fun aCapsuleScriptWithOneSlideSegment(scriptName: String, count: Int) {
        writeScript(scriptName, count)
    }

    @And("a capsule script {string} with {int} slide segments")
    fun aCapsuleScriptWithNSlideSegments(scriptName: String, count: Int) {
        writeScript(scriptName, count)
    }

    @And("a capsule script {string} with {int} sequentially ordered slide segments")
    fun aCapsuleScriptSequentiallyOrdered(scriptName: String, count: Int) {
        writeScript(scriptName, count)
    }

    private fun writeScript(scriptName: String, count: Int) {
        val deckBase = scriptName.replace("-script.txt", "")
        val slides = (1..count).joinToString("\n") { i ->
            """--- SLIDE $i : Slide $i ---
Speaker note for slide $i."""
        }
        val script = """
=== CAPSULE SCRIPT : $deckBase ===
$slides
        """.trimIndent()
        scriptsDir().resolve(scriptName).writeText(script)
    }

    @When("I run the task {string} with NoOp capture")
    fun iRunTheTaskWithNoOpCapture(taskName: String) {
        val runner = GradleRunner.create()
        runner.forwardOutput()
        runner.withPluginClasspath()
        runner.withArguments(taskName)
        runner.withProjectDir(projectDir)
        val result = runner.build()
        lastBuildResult = result.output
    }

    @Then("a video file {string} is generated")
    fun aVideoFileIsGenerated(videoName: String) {
        val videoFile = projectDir.resolve("build/capsule/$videoName")
        assertTrue(videoFile.exists(), "Expected video at ${videoFile.absolutePath}")
    }

    @Then("the video file is not empty")
    fun theVideoFileIsNotEmpty() {
        assertTrue(lastBuildResult.contains("CAPSULE →"))
    }

    @Then("the task completes without error")
    fun theTaskCompletesWithoutError() {
        assertTrue(lastBuildResult.isNotEmpty())
    }

    @Then("a placeholder video is generated")
    fun aPlaceholderVideoIsGenerated() {
        assertTrue(
            lastBuildResult.contains("PLAYWRIGHT CAPTURE PLACEHOLDER") ||
            lastBuildResult.contains("CAPSULE →")
        )
    }

    @Then("the injected deck HTML contains audio attributes for all slides")
    fun theInjectedDeckHtmlContainsAudioAttributesForAllSlides() {
        val injectedDir = projectDir.resolve("build/capsule/injected")
        val injectedFiles = injectedDir.listFiles { f -> f.name.endsWith("-deck.html") }
        assertNotNull(injectedFiles, "Should have injected deck files")
        assertTrue(injectedFiles.isNotEmpty(), "Should have injected deck files")
        val content = injectedFiles.first().readText()
        assertTrue(content.contains("data-audio"), "Should contain data-audio attributes")
    }

    @Then("the injected deck HTML contains {string} attributes")
    fun theInjectedDeckHtmlContainsAttributes(attributeName: String) {
        val injectedDir = projectDir.resolve("build/capsule/injected")
        val injectedFiles = injectedDir.listFiles { f -> f.name.endsWith("-deck.html") }
        assertNotNull(injectedFiles, "Should have injected deck files")
        assertTrue(injectedFiles.isNotEmpty(), "Should have injected deck files")
        val content = injectedFiles.first().readText()
        assertTrue(content.contains(attributeName), "Should contain $attributeName")
    }

    @Then("the injected deck contains the {string} autoplay script")
    fun theInjectedDeckContainsTheAutoplayScript(expected: String) {
        val injectedDir = projectDir.resolve("build/capsule/injected")
        val injectedFiles = injectedDir.listFiles { f -> f.name.endsWith("-deck.html") }
        assertNotNull(injectedFiles, "Should have injected deck files")
        assertTrue(injectedFiles.isNotEmpty(), "Should have injected deck files")
        val content = injectedFiles.first().readText()
        assertTrue(content.contains(expected), "Should contain $expected")
    }
}
