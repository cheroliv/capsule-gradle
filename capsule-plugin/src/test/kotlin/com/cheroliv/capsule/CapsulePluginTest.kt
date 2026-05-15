package com.cheroliv.capsule

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CapsulePluginTest {

    @Test
    fun `plugin registers capsulescript task`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("com.cheroliv.capsule")
        assertNotNull(project.tasks.findByName("capsulescript"))
    }

    @Test
    fun `plugin registers capsulebuild task`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("com.cheroliv.capsule")
        assertNotNull(project.tasks.findByName("capsulebuild"))
    }

    @Test
    fun `plugin registers capsulevideo task`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("com.cheroliv.capsule")
        val task = project.tasks.findByName("capsulevideo")
        assertNotNull(task)
        assertEquals("capsule", task.group)
    }

    @Test
    fun `plugin registers capsule extension`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("com.cheroliv.capsule")
        val ext = project.extensions.findByType(CapsuleExtension::class.java)
        assertNotNull(ext)
    }
}

class TtsEngineTest {

    @Test
    fun `noop engine creates placeholder file`() {
        val engine = NoOpTtsEngine()
        assertTrue(engine.isAvailable())
        assertEquals("noop", engine.name())

        val tmpFile = File.createTempFile("capsule-test", ".mp3")
        tmpFile.deleteOnExit()

        engine.synthesize("Bonjour le monde", tmpFile)

        assertTrue(tmpFile.exists())
        assertTrue(tmpFile.readText().contains("TTS PLACEHOLDER"))
        assertTrue(tmpFile.readText().contains("Bonjour le monde"))
    }

    @Test
    fun `noop engine creates parent directories`() {
        val engine = NoOpTtsEngine()
        val tmpDir = File.createTempFile("capsule-prefix", null)
        tmpDir.delete()
        tmpDir.mkdirs()
        tmpDir.deleteOnExit()
        val outputFile = tmpDir.resolve("subdir").resolve("test.mp3")

        engine.synthesize("Test", outputFile)

        assertTrue(outputFile.exists())
        assertTrue(outputFile.parentFile.exists())
    }

    @Test
    fun `piper engine reports unavailable when piper not installed`() {
        val engine = PiperTtsEngine(executablePath = "/nonexistent/path/piper")
        assertEquals(false, engine.isAvailable())
        assertEquals("piper", engine.name())
    }

    @Test
    fun `piper engine throws TtsException when not available`() {
        val engine = PiperTtsEngine(executablePath = "/nonexistent/path/piper")
        val tmpFile = File.createTempFile("capsule-test", ".mp3")
        tmpFile.deleteOnExit()

        try {
            engine.synthesize("Test", tmpFile)
            error("Expected TtsException")
        } catch (e: TtsException) {
            assertTrue(e.message!!.contains("not found"))
        }
    }

    @Test
    fun `capsule extension has expected defaults`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("com.cheroliv.capsule")
        val ext = project.extensions.getByType(CapsuleExtension::class.java)

        assertEquals("piper", ext.ttsEngine.get())
        assertEquals("fr_FR-siwis-medium", ext.ttsVoice.get())
        assertEquals("piper", ext.piperExecutablePath.get())
        assertEquals(true, ext.ttsFallbackEnabled.get())
        assertEquals("build/capsule", ext.outputDir.get())
        assertEquals("build/capsule", ext.sliderScriptDir.get())
        assertEquals(1408, ext.viewportWidth.get())
        assertEquals(792, ext.viewportHeight.get())
        assertEquals(120_000.0, ext.playwrightTimeout.get())
        assertEquals("", ext.chromiumExecutablePath.get())
        assertEquals("build/docs/asciidocRevealJs", ext.deckSourceDir.get())
    }
}

class PlaywrightCaptureTest {

    @Test
    fun `noop capture creates placeholder webm file`() {
        val capture = NoOpPlaywrightCapture()
        assertTrue(capture.isAvailable())
        assertEquals("noop-playwright", capture.name())

        val tmpDir = File.createTempFile("pw-test", null)
        tmpDir.delete()
        tmpDir.mkdirs()
        tmpDir.deleteOnExit()

        capture.capture("/fake/deck.html", tmpDir, 1408, 792, 3)

        val placeholder = tmpDir.resolve("capsule.webm")
        assertTrue(placeholder.exists())
        val content = placeholder.readText()
        assertTrue(content.contains("PLAYWRIGHT CAPTURE PLACEHOLDER"))
        assertTrue(content.contains("/fake/deck.html"))
        assertTrue(content.contains("Slides: 3"))
    }

    @Test
    fun `noop capture creates output directory if absent`() {
        val capture = NoOpPlaywrightCapture()
        val tmpDir = File.createTempFile("pw-mkdir", null)
        tmpDir.delete()
        tmpDir.deleteOnExit()

        capture.capture("/deck.html", tmpDir, 1024, 768, 1)

        assertTrue(tmpDir.exists())
        assertTrue(tmpDir.isDirectory)
        assertTrue(tmpDir.resolve("capsule.webm").exists())
    }

    @Test
    fun `noop capture close is noop`() {
        val capture = NoOpPlaywrightCapture()
        capture.close()
    }
}

class CapsuleVideoTaskTest {

    @TempDir
    lateinit var tempDir: java.io.File

    private fun createTask(
        deckDir: java.io.File,
        scriptDir: java.io.File,
        capture: PlaywrightCapture? = null,
        engine: TtsEngine? = null
    ): CapsuleVideoTask {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        val ext = CapsuleExtension(project.objects)
        ext.deckSourceDir.set(deckDir.absolutePath)
        ext.sliderScriptDir.set(scriptDir.absolutePath)
        ext.ttsEngine.set("noop")
        ext.outputDir.set("capsule")

        val t = project.tasks.register("capsulevideo", CapsuleVideoTask::class.java).get()
        t.capsuleExtension = ext
        if (capture != null) t.playwrightCapture = capture
        if (engine != null) t.ttsEngine = engine
        return t
    }

    @Test
    fun `execute warns when no deck files found`() {
        val emptyDir = java.io.File(tempDir, "empty-decks").also { it.mkdirs() }
        val scriptDir = java.io.File(tempDir, "scripts").also { it.mkdirs() }
        val task = createTask(emptyDir, scriptDir)
        task.execute()
    }

    @Test
    fun `execute warns when no script files found`() {
        val deckDir = java.io.File(tempDir, "decks").also { it.mkdirs() }
        java.io.File(deckDir, "test-deck.html").writeText("<html></html>")
        val scriptDir = java.io.File(tempDir, "scripts").also { it.mkdirs() }
        val task = createTask(deckDir, scriptDir)
        task.execute()
    }

    @Test
    fun `execute produces webm with noop capture`() {
        val deckDir = java.io.File(tempDir, "decks").also { it.mkdirs() }
        val deckFile = java.io.File(deckDir, "mon-cours-deck.html")
        deckFile.writeText("""
<html><body>
<div class="reveal">
  <div class="slides">
    <section data-capsule-slide="1"><h2>Intro</h2></section>
    <section data-capsule-slide="2"><h2>Topic</h2></section>
  </div>
</div>
</body></html>
        """.trimIndent())

        val scriptDir = java.io.File(tempDir, "scripts").also { it.mkdirs() }
        val scriptFile = java.io.File(scriptDir, "mon-cours-script.txt")
        scriptFile.writeText("""
=== CAPSULE SCRIPT : mon-cours ===
--- SLIDE 1 : Intro ---
Bienvenue dans la formation.
--- SLIDE 2 : Topic ---
Voici le contenu principal.
        """.trimIndent())

        val task = createTask(
            deckDir,
            scriptDir,
            capture = NoOpPlaywrightCapture(),
            engine = NoOpTtsEngine()
        )
        task.execute()

        val expectedVideo = java.io.File(tempDir, "build/capsule/mon-cours.webm")
        assertTrue(expectedVideo.exists(), "Expected video at ${expectedVideo.absolutePath}")
        assertTrue(expectedVideo.readText().contains("PLAYWRIGHT CAPTURE PLACEHOLDER"))

        val injectedDir = java.io.File(tempDir, "build/capsule/injected")
        assertTrue(injectedDir.exists())
        val injectedDeck = java.io.File(injectedDir, "mon-cours-deck.html")
        assertTrue(injectedDeck.exists())
        assertTrue(injectedDeck.readText().contains("data-audio"))
    }
}
