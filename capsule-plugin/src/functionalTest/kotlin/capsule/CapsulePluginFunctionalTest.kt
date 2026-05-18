package capsule

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
                id('cccp.education.capsule')
            }
            $extraConfig
        """.trimIndent())
    }

    @Test
    fun `can run generateCapsuleScript task`() {
        setupBuild()

        val runner = GradleRunner.create()
        runner.forwardOutput()
        runner.withPluginClasspath()
        runner.withArguments("generateCapsuleScript")
        runner.withProjectDir(projectDir)
        val result = runner.build()

        assertTrue(result.output.contains("No *-script.txt files found"))
    }

    @Test
    fun `generateCapsule task depends on generateCapsuleScript`() {
        setupBuild()

        val runner = GradleRunner.create()
        runner.forwardOutput()
        runner.withPluginClasspath()
        runner.withArguments("generateCapsule")
        runner.withProjectDir(projectDir)
        val result = runner.build()

        assertTrue(result.output.contains("generateCapsuleScript") || result.output.contains("No capsule scripts"))
    }

    @Test
    fun `generateCapsule with noop engine falls back gracefully`() {
        setupBuild("""
            capsule {
                ttsEngine = "noop"
            }
        """.trimIndent())

        val runner = GradleRunner.create()
        runner.forwardOutput()
        runner.withPluginClasspath()
        runner.withArguments("generateCapsule")
        runner.withProjectDir(projectDir)
        val result = runner.build()

        assertTrue(result.output.contains("No capsule scripts"))
    }

    @Test
    fun `can run generateCapsuleVideo task`() {
        setupBuild("""
            capsule {
                ttsEngine = "noop"
            }
        """.trimIndent())

        val runner = GradleRunner.create()
        runner.forwardOutput()
        runner.withPluginClasspath()
        runner.withArguments("generateCapsuleVideo")
        runner.withProjectDir(projectDir)
        val result = runner.build()

        assertTrue(result.output.contains("No *-deck.html files found") || result.output.contains("No *-script.txt files found"))
    }

    @Test
    fun `can run deployCapsule task`() {
        setupBuild("""
            capsule {
                ttsEngine = "noop"
            }
        """.trimIndent())

        val runner = GradleRunner.create()
        runner.forwardOutput()
        runner.withPluginClasspath()
        runner.withArguments("deployCapsule")
        runner.withProjectDir(projectDir)
        val result = runner.build()

        assertTrue(result.output.contains("No capsule videos found") || result.output.contains("UP-TO-DATE"))
    }

    @Test
    fun `can run collectCapsuleContext task`() {
        setupBuild("""
            capsule {
                ttsEngine = "noop"
            }
        """.trimIndent())

        val runner = GradleRunner.create()
        runner.forwardOutput()
        runner.withPluginClasspath()
        runner.withArguments("collectCapsuleContext")
        runner.withProjectDir(projectDir)
        val result = runner.build()

        assertTrue(result.output.contains("CAPSULE COMPOSITE CONTEXT") || result.output.contains("UP-TO-DATE"))
    }

    @Test
    fun `transformCapsuleContext produces valid json output`() {
        setupBuild()

        val runner = GradleRunner.create()
        runner.forwardOutput()
        runner.withPluginClasspath()
        runner.withArguments("transformCapsuleContext")
        runner.withProjectDir(projectDir)
        val result = runner.build()

        assertTrue(result.output.contains("CAPSULE PARSE CONTEXT") || result.output.contains("UP-TO-DATE"))
    }

    @Test
    fun `collectCapsuleRetrieve with outputFile parameter`() {
        setupBuild()

        val runner = GradleRunner.create()
        runner.forwardOutput()
        runner.withPluginClasspath()
        runner.withArguments("collectCapsuleRetrieve", "-PoutputFile=build/capsule/retrieve-results.json")
        runner.withProjectDir(projectDir)
        val result = runner.build()

        assertTrue(result.output.contains("CAPSULE PARSE CONTEXT") || result.output.contains("UP-TO-DATE"))
    }
}

class CapsuleAudioConstraintFunctionalTest {

    @field:TempDir
    lateinit var projectDir: File

    private fun setup() {
        projectDir.resolve("settings.gradle").writeText("")
        projectDir.resolve("build.gradle").writeText("""
            plugins {
                id('cccp.education.capsule')
            }
            capsule {
                ttsEngine = "espeak"
                outputDir = "capsules"
            }
        """.trimIndent())
    }

    @Test
    fun `espeak TTS must produce real audio not text placeholder`() {
        setup()

        val scriptDir = projectDir.resolve("build/capsule").also { it.mkdirs() }
        scriptDir.resolve("audio-test-script.txt").writeText("""
=== CAPSULE SCRIPT : audio-test ===
--- SLIDE 1 : Test ---
Ceci est un test de synthese vocale.
        """.trimIndent())

        val decksDir = projectDir.resolve("build/docs/asciidocRevealJs").also { it.mkdirs() }
        decksDir.resolve("audio-test-deck.html").writeText("""
<html><body>
<div class="reveal">
  <div class="slides">
    <section data-capsule-slide="1"><h2>Test</h2></section>
  </div>
</div>
</body></html>
        """.trimIndent())

        val runner = GradleRunner.create()
        runner.forwardOutput()
        runner.withPluginClasspath()
        runner.withArguments("generateCapsule")
        runner.withProjectDir(projectDir)
        val result = runner.build()

        val capDir = projectDir.resolve("build/capsules/audio-test")
        val mp3Files = capDir.listFiles { f -> f.name.endsWith(".mp3") }
        assertTrue(mp3Files != null && mp3Files.isNotEmpty(), "Must produce MP3 files in capsules/")
        val mp3 = mp3Files.first()
        assertTrue(mp3.length() > 500, "Real audio must be > 500 bytes, got ${mp3.length()}")

        val content = mp3.readText(Charsets.ISO_8859_1)
        assertTrue(!content.contains("TTS PLACEHOLDER"), "Must not be a text placeholder")
    }
}

class CapsuleVideoOutputConstraintFunctionalTest {

    @field:TempDir
    lateinit var projectDir: File

    private val webmSignature = byteArrayOf(0x1a.toByte(), 0x45.toByte(), 0xdf.toByte(), 0xa3.toByte())

    @Test
    fun `generateCapsuleVideo task must output to capsules directory not build`() {
        projectDir.resolve("settings.gradle").writeText("")
        projectDir.resolve("build.gradle").writeText("""
            plugins {
                id('cccp.education.capsule')
            }
            capsule {
                ttsEngine = "noop"
                outputDir = "capsules"
            }
        """.trimIndent())

        val scriptDir = projectDir.resolve("build/capsule").also { it.mkdirs() }
        scriptDir.resolve("test-script.txt").writeText("""
=== CAPSULE SCRIPT : test ===
--- SLIDE 1 : Title ---
Note content.
        """.trimIndent())

        val decksDir = projectDir.resolve("build/docs/asciidocRevealJs").also { it.mkdirs() }
        decksDir.resolve("test-deck.html").writeText("""
<html><body>
<div class="reveal">
  <div class="slides">
    <section data-capsule-slide="1"><h2>Title</h2></section>
  </div>
</div>
</body></html>
        """.trimIndent())

        val runner = GradleRunner.create()
        runner.forwardOutput()
        runner.withPluginClasspath()
        runner.withArguments("generateCapsuleVideo")
        runner.withProjectDir(projectDir)
        val result = runner.build()

        val capFile = projectDir.resolve("capsules/test.webm")
        assertTrue(capFile.exists(), "Video must be in capsules/ not build/, expected: ${capFile.absolutePath}")
    }
}
