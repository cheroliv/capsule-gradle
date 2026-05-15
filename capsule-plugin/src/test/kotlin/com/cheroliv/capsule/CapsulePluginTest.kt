package com.cheroliv.capsule

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Tag
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
    fun `plugin registers capsule video task`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("com.cheroliv.capsule")
        val task = project.tasks.findByName("capsulevideo")
        assertNotNull(task)
        assertEquals("capsule", task.group)
    }

    @Test
    fun `plugin registers capsuledistrib task`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("com.cheroliv.capsule")
        val task = project.tasks.findByName("capsuledistrib")
        assertNotNull(task)
        assertEquals("capsule", task.group)
    }

    @Test
    fun `plugin registers capsulecompositecontext task`() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("com.cheroliv.capsule")
        val task = project.tasks.findByName("capsulecompositecontext")
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
        assertEquals("capsule", ext.outputDir.get())
        assertEquals("capsule", ext.sliderScriptDir.get())
        assertEquals(1408, ext.viewportWidth.get())
        assertEquals(792, ext.viewportHeight.get())
        assertEquals(120_000.0, ext.playwrightTimeout.get())
        assertEquals("", ext.chromiumExecutablePath.get())
        assertEquals("docs/asciidocRevealJs", ext.deckSourceDir.get())
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

    @Test
    fun `sequential fallback injects audio into sections without data-capsule-slide`() {
        val deckDir = java.io.File(tempDir, "decks").also { it.mkdirs() }
        val deckFile = java.io.File(deckDir, "cours-deck.html")
        deckFile.writeText("""
<html><body>
<div class="reveal">
  <div class="slides">
    <section><h2>Slide 1</h2></section>
    <section><h2>Slide 2</h2></section>
    <section><h2>Slide 3</h2></section>
  </div>
</div>
</body></html>
        """.trimIndent())

        val scriptDir = java.io.File(tempDir, "scripts").also { it.mkdirs() }
        val scriptFile = java.io.File(scriptDir, "cours-script.txt")
        scriptFile.writeText("""
=== CAPSULE SCRIPT : cours ===
--- SLIDE 1 : Slide 1 ---
Contenu slide 1.
--- SLIDE 2 : Slide 2 ---
Contenu slide 2.
--- SLIDE 3 : Slide 3 ---
Contenu slide 3.
        """.trimIndent())

        val task = createTask(
            deckDir,
            scriptDir,
            capture = NoOpPlaywrightCapture(),
            engine = NoOpTtsEngine()
        )
        task.execute()

        val injectedDir = java.io.File(tempDir, "build/capsule/injected")
        assertTrue(injectedDir.exists())
        val injectedDeck = java.io.File(injectedDir, "cours-deck.html")
        assertTrue(injectedDeck.exists())
        val injectedContent = injectedDeck.readText()
        assertTrue(injectedContent.contains("data-audio"), "Should have audio attributes")
        assertTrue(injectedContent.contains("sequential fallback"))
        assertTrue(injectedContent.count { it == '\n' && injectedContent.contains("<section data-audio=") } >= 2)
    }

    @Test
    fun `multi-deck build produces separate videos`() {
        val deckDir = java.io.File(tempDir, "decks").also { it.mkdirs() }
        val deck1 = java.io.File(deckDir, "cours-a-deck.html")
        deck1.writeText("""
<html><body>
<div class="reveal">
  <div class="slides">
    <section data-capsule-slide="1"><h2>A1</h2></section>
  </div>
</div>
</body></html>
        """.trimIndent())
        val deck2 = java.io.File(deckDir, "cours-b-deck.html")
        deck2.writeText("""
<html><body>
<div class="reveal">
  <div class="slides">
    <section data-capsule-slide="1"><h2>B1</h2></section>
  </div>
</div>
</body></html>
        """.trimIndent())

        val scriptDir = java.io.File(tempDir, "scripts").also { it.mkdirs() }
        java.io.File(scriptDir, "cours-a-script.txt").writeText("""
=== CAPSULE SCRIPT : cours-a ===
--- SLIDE 1 : A1 ---
Deck A.
        """.trimIndent())
        java.io.File(scriptDir, "cours-b-script.txt").writeText("""
=== CAPSULE SCRIPT : cours-b ===
--- SLIDE 1 : B1 ---
Deck B.
        """.trimIndent())

        val task = createTask(
            deckDir,
            scriptDir,
            capture = NoOpPlaywrightCapture(),
            engine = NoOpTtsEngine()
        )
        task.execute()

        val capDir = java.io.File(tempDir, "build/capsule")
        val videoA = java.io.File(capDir, "cours-a.webm")
        val videoB = java.io.File(capDir, "cours-b.webm")
        assertTrue(videoA.exists(), "Expected video for cours-a")
        assertTrue(videoB.exists(), "Expected video for cours-b")
        assertTrue(videoA.readText().contains("PLAYWRIGHT CAPTURE PLACEHOLDER"))
        assertTrue(videoB.readText().contains("PLAYWRIGHT CAPTURE PLACEHOLDER"))
    }

    @Test
    @Tag("integration")
    fun `playwright capture produces valid webm when chromium available`() {
        val impl = PlaywrightCaptureImpl()
        if (!impl.isAvailable()) {
            return
        }

        val deckDir = java.io.File(tempDir, "integration-decks").also { it.mkdirs() }
        val deckFile = java.io.File(deckDir, "test-deck.html")
        deckFile.writeText("""
<html><head>
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/reveal.js@5.1.0/dist/reveal.css">
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/reveal.js@5.1.0/dist/theme/white.css">
</head><body>
<div class="reveal">
  <div class="slides">
    <section data-capsule-slide="1"><h2>Slide 1</h2></section>
    <section data-capsule-slide="2"><h2>Slide 2</h2></section>
  </div>
</div>
<script src="https://cdn.jsdelivr.net/npm/reveal.js@5.1.0/dist/reveal.js"></script>
<script>
  Reveal.initialize({ autoSlide: 500, autoSlideStoppable: false });
</script>
</body></html>
        """.trimIndent())

        val outputDir = java.io.File(tempDir, "integration-video").also { it.mkdirs() }

        try {
            impl.capture(deckFile.absolutePath, outputDir, 1408, 792, 2)
            impl.close()

            val videoFiles = outputDir.listFiles { f -> f.name.endsWith(".webm") }
            assertNotNull(videoFiles)
            assertTrue(videoFiles.isNotEmpty(), "Should produce a webm video file")
            val video = videoFiles.first()
            assertTrue(video.length() > 0, "Video file should have non-zero size")
        } catch (e: CapturingException) {
            impl.close()
            throw e
        }
    }
}

class CapsuleDistribTaskTest {

    @TempDir
    lateinit var tempDir: java.io.File

    private fun createTask(
        captDir: java.io.File,
        ffmpegAvailable: Boolean = false
    ): CapsuleDistribTask {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        val ext = CapsuleExtension(project.objects)
        ext.outputDir.set("capsule")
        ext.ffmpegExecutablePath.set(if (ffmpegAvailable) "ffmpeg" else "__no_ffmpeg__")

        val t = project.tasks.register("capsuledistrib", CapsuleDistribTask::class.java).get()
        t.capsuleExtension = ext
        return t
    }

    @Test
    fun `execution warns when no videos found`() {
        val capDir = java.io.File(tempDir, "build/capsule").also { it.mkdirs() }
        val task = createTask(capDir)
        task.execute()
    }

    @Test
    fun `execution copies videos when ffmpeg not available`() {
        val capDir = java.io.File(tempDir, "build/capsule").also { it.mkdirs() }
        val videoFile = java.io.File(capDir, "test.webm")
        videoFile.writeText("fake webm content")

        val task = createTask(capDir, ffmpegAvailable = false)
        task.execute()

        val distFile = java.io.File(tempDir, "build/capsule/distrib/test.webm")
        assertTrue(distFile.exists(), "Video should be copied to distrib dir")
        assertEquals("fake webm content", distFile.readText())
    }

    @Test
    fun `execution handles multiple videos`() {
        val capDir = java.io.File(tempDir, "build/capsule").also { it.mkdirs() }
        java.io.File(capDir, "deck-a.webm").writeText("video a")
        java.io.File(capDir, "deck-b.webm").writeText("video b")

        val task = createTask(capDir, ffmpegAvailable = false)
        task.execute()

        val distribA = java.io.File(tempDir, "build/capsule/distrib/deck-a.webm")
        val distribB = java.io.File(tempDir, "build/capsule/distrib/deck-b.webm")
        assertTrue(distribA.exists())
        assertTrue(distribB.exists())
        assertEquals("video a", distribA.readText())
        assertEquals("video b", distribB.readText())
    }
}

class CapsuleCompositeContextTaskTest {

    @TempDir
    lateinit var tempDir: java.io.File

    private fun createTask(
        scriptDir: java.io.File,
        captDir: java.io.File,
        distribDir: java.io.File
    ): CapsuleCompositeContextTask {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        project.version = "0.1.0-SNAPSHOT"

        val ext = CapsuleExtension(project.objects)
        ext.outputDir.set("capsule")
        ext.sliderScriptDir.set(scriptDir.relativeTo(project.layout.buildDirectory.get().asFile).path)
        ext.viewportWidth.set(1408)
        ext.viewportHeight.set(792)
        ext.ttsEngine.set("piper")
        ext.ttsVoice.set("fr_FR-siwis-medium")

        val t = project.tasks.register("capsulecompositecontext", CapsuleCompositeContextTask::class.java).get()
        t.capsuleExtension = ext
        return t
    }

    @Test
    fun `execution creates json context file`() {
        val buildDir = java.io.File(tempDir, "build")
        val scriptDir = java.io.File(buildDir, "capsule").also { it.mkdirs() }
        val capDir = java.io.File(buildDir, "capsule").also { it.mkdirs() }
        val distDir = java.io.File(buildDir, "capsule/distrib").also { it.mkdirs() }

        val scriptFile = java.io.File(scriptDir, "test-cours-script.txt")
        scriptFile.writeText("""
=== CAPSULE SCRIPT : test-cours ===
--- SLIDE 1 : Introduction ---
Bienvenue dans le cours.
--- SLIDE 2 : Contenu ---
Voici le contenu principal de la formation.
        """.trimIndent())

        val task = createTask(scriptDir, capDir, distDir)
        task.execute()

        val jsonFile = java.io.File(buildDir, "capsule/capsule-context.json")
        assertTrue(jsonFile.exists(), "JSON context file should be created")
        val content = jsonFile.readText()
        assertTrue(content.contains("capsule"), "Should contain source=capsule")
        assertTrue(content.contains("test-cours"), "Should contain deck name")
        assertTrue(content.contains("slideCount"), "Should contain slide count")
        assertTrue(content.contains("Introduction"), "Should contain slide title")
        assertTrue(content.contains("piper"), "Should contain TTS engine")
    }

    @Test
    fun `execution handles multiple decks`() {
        val buildDir = java.io.File(tempDir, "build")
        val scriptDir = java.io.File(buildDir, "capsule").also { it.mkdirs() }
        val capDir = java.io.File(buildDir, "capsule").also { it.mkdirs() }
        val distDir = java.io.File(buildDir, "capsule/distrib").also { it.mkdirs() }

        java.io.File(scriptDir, "cours-a-script.txt").writeText("""
=== CAPSULE SCRIPT : cours-a ===
--- SLIDE 1 : A1 ---
Deck A.
        """.trimIndent())
        java.io.File(scriptDir, "cours-b-script.txt").writeText("""
=== CAPSULE SCRIPT : cours-b ===
--- SLIDE 1 : B1 ---
Deck B.
        """.trimIndent())

        val task = createTask(scriptDir, capDir, distDir)
        task.execute()

        val jsonFile = java.io.File(buildDir, "capsule/capsule-context.json")
        val content = jsonFile.readText()
        assertTrue(content.contains("cours-a"))
        assertTrue(content.contains("cours-b"))
    }

    @Test
    fun `execution includes distribVideo path when available`() {
        val buildDir = java.io.File(tempDir, "build")
        val scriptDir = java.io.File(buildDir, "capsule").also { it.mkdirs() }
        val capDir = java.io.File(buildDir, "capsule").also { it.mkdirs() }
        val distDir = java.io.File(buildDir, "capsule/distrib").also { it.mkdirs() }

        java.io.File(distDir, "cours.webm").writeText("fake distrib video")

        java.io.File(scriptDir, "cours-script.txt").writeText("""
=== CAPSULE SCRIPT : cours ===
--- SLIDE 1 : X ---
Note.
        """.trimIndent())

        val task = createTask(scriptDir, capDir, distDir)
        task.execute()

        val content = java.io.File(buildDir, "capsule/capsule-context.json").readText()
        assertTrue(content.contains("distribVideo"))
        assertTrue(content.contains("distrib"))
    }
}

class CapsuleParseContextTaskTest {

    @TempDir
    lateinit var tempDir: java.io.File

    private fun createTask(
        contextJsonFile: java.io.File,
        outputFile: java.io.File
    ): CapsuleParseContextTask {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        val t = project.tasks.register("capsuleparsecontext", CapsuleParseContextTask::class.java).get()
        t.contextFile.set(contextJsonFile)
        t.outputFile.set(outputFile)
        return t
    }

    @Test
    fun `parses capsule-context json and outputs list of maps`() {
        val contextFile = java.io.File(tempDir, "capsule-context.json")
        contextFile.writeText("""
{
  "source": "capsule",
  "version": "0.1.0",
  "entries": [
    {
      "source": "capsule",
      "deckName": "mon-cours",
      "slideCount": 2,
      "originalVideo": "/build/capsule/mon-cours.webm",
      "distribVideo": "/build/capsule/distrib/mon-cours.webm",
      "viewport": { "width": 1408, "height": 792 },
      "distribDimensions": { "width": 1080, "height": 1920 },
      "slides": [
        { "index": 1, "title": "Intro", "speakerNoteLength": 50 },
        { "index": 2, "title": "Content", "speakerNoteLength": 80 }
      ],
      "ttsEngine": "piper",
      "ttsVoice": "fr_FR-siwis-medium"
    }
  ],
  "timestamp": 1234567890
}
        """.trimIndent())

        val outputFile = java.io.File(tempDir, "parsed-results.json")

        val task = createTask(contextFile, outputFile)
        task.execute()

        assertTrue(outputFile.exists(), "Output file should be created")
        val content = outputFile.readText()
        assertTrue(content.contains("mon-cours"), "Should contain deck name")
        assertTrue(content.contains("capsule"), "Should contain source=capsule")
        assertTrue(content.contains("originalVideo"), "Should contain originalVideo path")
        assertTrue(content.contains("slideCount"), "Should contain slideCount")
    }

    @Test
    fun `returns empty list when no entries`() {
        val contextFile = java.io.File(tempDir, "capsule-context.json")
        contextFile.writeText("""
{
  "source": "capsule",
  "version": "0.1.0",
  "entries": [],
  "timestamp": 1234567890
}
        """.trimIndent())

        val outputFile = java.io.File(tempDir, "parsed-results.json")

        val task = createTask(contextFile, outputFile)
        task.execute()

        assertTrue(outputFile.exists())
        val content = outputFile.readText()
        assertTrue(content.contains("[]") || content.contains("[ ]"))
    }

    @Test
    fun `handles missing file gracefully`() {
        val contextFile = java.io.File(tempDir, "nonexistent.json")
        val outputFile = java.io.File(tempDir, "parsed-results.json")

        val task = createTask(contextFile, outputFile)
        task.execute()

        assertTrue(outputFile.exists())
        val content = outputFile.readText()
        assertTrue(content.contains("[]") || content.contains("[ ]"))
    }
}
