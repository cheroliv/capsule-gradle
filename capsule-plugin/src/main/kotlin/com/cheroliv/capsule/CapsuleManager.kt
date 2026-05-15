package com.cheroliv.capsule

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File

class CapsuleManager(private val project: Project) {

    private val capsuleExt = project.extensions.getByType(CapsuleExtension::class.java)

    fun registerTasks() {
        project.registerCapsuleScriptTask()
        project.registerCapsuleBuildTask()
        project.registerCapsuleVideoTask()
    }

    private fun Project.registerCapsuleScriptTask() {
        tasks.register("capsulescript", CapsuleScriptTask::class.java) { task ->
            task.group = "capsule"
            task.description = "Reads *-script.txt produced by slider-gradle and validates the capsule script"
            task.capsuleExtension = this@CapsuleManager.capsuleExt
        }
    }

    private fun Project.registerCapsuleBuildTask() {
        tasks.register("capsulebuild", CapsuleBuildTask::class.java) { task ->
            task.group = "capsule"
            task.description = "Generates TTS audio files from capsule scripts (Piper placeholder)"
            task.dependsOn("capsulescript")
            task.capsuleExtension = this@CapsuleManager.capsuleExt
        }
    }

    private fun Project.registerCapsuleVideoTask() {
        tasks.register("capsulevideo", CapsuleVideoTask::class.java) { task ->
            task.group = "capsule"
            task.description = "Injects TTS audio into deck HTML then captures video via Playwright Java"
            task.dependsOn("capsulebuild")
            task.capsuleExtension = this@CapsuleManager.capsuleExt
        }
    }

    companion object {
        fun readScriptFiles(dir: File): List<File> {
            return dir.listFiles { f -> f.name.endsWith("-script.txt") }
                ?.toList() ?: emptyList()
        }

        fun parseScript(file: File): CapsuleScript {
            val lines = file.readLines()
            val deckName = lines.firstOrNull()
                ?.removePrefix("=== CAPSULE SCRIPT : ")
                ?.removeSuffix(" ===")
                ?.trim() ?: file.nameWithoutExtension

            val slides = mutableListOf<SlideSegment>()
            var currentIndex = -1
            var currentTitle = ""
            val noteLines = mutableListOf<String>()

            for (i in 1 until lines.size) {
                val line = lines[i]
                when {
                    line.startsWith("--- SLIDE ") && line.contains(":") -> {
                        if (currentIndex >= 0) {
                            slides.add(
                                SlideSegment(
                                    index = currentIndex,
                                    title = currentTitle,
                                    speakerNote = noteLines.joinToString("\n").trim()
                                )
                            )
                            noteLines.clear()
                        }
                        val parts = line.removeSurrounding("--- SLIDE ", " ---")
                        val colonIdx = parts.indexOf(":")
                        currentIndex = parts.substring(0, colonIdx).trim()
                            .toIntOrNull() ?: (slides.size + 1)
                        currentTitle = parts.substring(colonIdx + 1).trim()
                    }
                    line.isNotBlank() && currentIndex >= 0 -> noteLines.add(line)
                }
            }

            if (currentIndex >= 0) {
                slides.add(
                    SlideSegment(
                        index = currentIndex,
                        title = currentTitle,
                        speakerNote = noteLines.joinToString("\n").trim()
                    )
                )
            }

            return CapsuleScript(deckName, slides)
        }
    }
}

@org.gradle.work.DisableCachingByDefault(because = "Filesystem-bound: reads slider output and produces TTS artifacts")
open class CapsuleScriptTask : DefaultTask() {

    @get:OutputDirectory
    val outputDir: DirectoryProperty = project.objects.directoryProperty()

    @get:org.gradle.api.tasks.Internal
    lateinit var capsuleExtension: CapsuleExtension

    init {
        outputDir.convention(project.layout.buildDirectory.dir("capsule"))
    }

    @TaskAction
    fun execute() {
        val scriptDir = resolveScriptDir()
        val scripts = CapsuleManager.readScriptFiles(scriptDir)

        if (scripts.isEmpty()) {
            logger.warn("No *-script.txt files found in {}", scriptDir.absolutePath)
            logger.warn("Run 'asciidocCapsule' from slider-gradle first.")
            return
        }

        for (script in scripts) {
            val parsed = CapsuleManager.parseScript(script)
            logger.lifecycle(
                "Capsule script '{}' → {} slides", parsed.deckName, parsed.slides.size
            )
            for (seg in parsed.slides) {
                logger.lifecycle(
                    "  [{}] {} ({} chars)", seg.index, seg.title, seg.speakerNote.length
                )
            }
        }
    }

    private fun resolveScriptDir(): File {
        val configured = capsuleExtension.sliderScriptDir.get()

        val candidate = project.layout.buildDirectory.dir(configured).get().asFile
        if (candidate.exists() && candidate.listFiles()
                ?.any { it.name.endsWith("-script.txt") } == true
        ) {
            return candidate
        }

        val sliderOutput = project.rootProject.projectDir.parentFile
            ?.resolve("slider-plugin")
            ?.resolve("slider")
            ?.resolve("build")
            ?.resolve("capsule")
        if (sliderOutput != null && sliderOutput.exists()) return sliderOutput

        return candidate
    }
}

@org.gradle.work.DisableCachingByDefault(because = "Filesystem-bound: reads script files and writes TTS placeholder audio")
open class CapsuleBuildTask : DefaultTask() {

    @get:OutputDirectory
    val outputDir: DirectoryProperty = project.objects.directoryProperty()

    @get:org.gradle.api.tasks.Internal
    lateinit var capsuleExtension: CapsuleExtension

    init {
        outputDir.convention(project.layout.buildDirectory.dir("capsule"))
    }

    @get:org.gradle.api.tasks.Internal
    internal var ttsEngine: TtsEngine? = null

    private fun resolveTtsEngine(): TtsEngine {
        if (ttsEngine != null) return ttsEngine!!

        val configuredEngine = capsuleExtension.ttsEngine.get()
        return when (configuredEngine.lowercase()) {
            "piper" -> {
                val piperPath = capsuleExtension.piperExecutablePath.get()
                val voice = capsuleExtension.ttsVoice.get()
                val engine = PiperTtsEngine(piperPath, voice)
                if (engine.isAvailable()) {
                    logger.lifecycle("TTS engine: piper → {}", piperPath)
                    engine
                } else if (capsuleExtension.ttsFallbackEnabled.get()) {
                    logger.warn("Piper not available at {}, falling back to noop placeholder", piperPath)
                    NoOpTtsEngine()
                } else {
                    throw TtsException("Piper not available at: $piperPath and fallback is disabled")
                }
            }
            "noop" -> {
                logger.lifecycle("TTS engine: noop (placeholder)")
                NoOpTtsEngine()
            }
            else -> {
                logger.warn("Unknown TTS engine '{}', using noop placeholder", configuredEngine)
                NoOpTtsEngine()
            }
        }
    }

    @TaskAction
    fun execute() {
        val scriptDir = resolveScriptDir()
        val scripts = CapsuleManager.readScriptFiles(scriptDir)

        if (scripts.isEmpty()) {
            logger.warn("No capsule scripts to process. Skipping TTS generation.")
            return
        }

        val outDir = project.layout.buildDirectory.dir(
            capsuleExtension.outputDir.get()
        ).get().asFile
        outDir.mkdirs()

        val engine = resolveTtsEngine()
        logger.lifecycle("TTS engine: {}", engine.name())

        var totalSynthesized = 0
        var totalFailed = 0

        for (script in scripts) {
            val parsed = CapsuleManager.parseScript(script)
            val deckOutputDir = outDir.resolve(parsed.deckName)
            deckOutputDir.mkdirs()

            for (seg in parsed.slides) {
                val idx = String.format("%02d", seg.index)
                val ttsFile = deckOutputDir.resolve("slide-$idx.mp3")

                try {
                    engine.synthesize(seg.speakerNote, ttsFile)
                    totalSynthesized++
                    logger.lifecycle("  TTS → {} ({} chars)", ttsFile.name, seg.speakerNote.length)
                } catch (e: TtsException) {
                    totalFailed++
                    logger.error("  TTS FAILED slide {}: {}", seg.index, e.message)
                    if (!capsuleExtension.ttsFallbackEnabled.get()) throw e
                }
            }
        }

        logger.lifecycle(
            "TTS generation: {} synthesized, {} failed, {} engine",
            totalSynthesized, totalFailed, engine.name()
        )
    }

    private fun resolveScriptDir(): File {
        val configured = capsuleExtension.sliderScriptDir.get()

        val candidate = project.layout.buildDirectory.dir(configured).get().asFile
        if (candidate.exists() && candidate.listFiles()
                ?.any { it.name.endsWith("-script.txt") } == true
        ) {
            return candidate
        }

        val sliderOutput = project.rootProject.projectDir.parentFile
            ?.resolve("slider-plugin")
            ?.resolve("slider")
            ?.resolve("build")
            ?.resolve("capsule")
        if (sliderOutput != null && sliderOutput.exists()) return sliderOutput

        return candidate
    }
}

@org.gradle.work.DisableCachingByDefault(because = "Filesystem-bound: injects audio into HTML deck and captures video via Playwright")
open class CapsuleVideoTask : DefaultTask() {

    @get:OutputFile
    val outputFile: org.gradle.api.file.RegularFileProperty = project.objects.fileProperty()

    @get:org.gradle.api.tasks.Internal
    lateinit var capsuleExtension: CapsuleExtension

    @get:org.gradle.api.tasks.Internal
    internal var playwrightCapture: PlaywrightCapture? = null

    @get:org.gradle.api.tasks.Internal
    internal var ttsEngine: TtsEngine? = null

    init {
        outputFile.convention(
            project.layout.buildDirectory.file("capsule/capsule.webm")
        )
    }

    private fun resolvePlaywrightCapture(): PlaywrightCapture {
        if (playwrightCapture != null) return playwrightCapture!!

        if (capsuleExtension.ttsEngine.get().lowercase() == "noop") {
            logger.lifecycle("TTS engine is noop — using noop capture fallback")
            return NoOpPlaywrightCapture()
        }

        val impl = PlaywrightCaptureImpl(
            timeout = capsuleExtension.playwrightTimeout.get(),
        )
        return if (impl.isAvailable()) {
            impl
        } else {
            NoOpPlaywrightCapture()
        }
    }

    private fun resolveTtsEngine(): TtsEngine {
        if (ttsEngine != null) return ttsEngine!!

        return when (capsuleExtension.ttsEngine.get().lowercase()) {
            "piper" -> {
                val engine = PiperTtsEngine(
                    capsuleExtension.piperExecutablePath.get(),
                    capsuleExtension.ttsVoice.get()
                )
                if (engine.isAvailable()) engine else NoOpTtsEngine()
            }
            else -> NoOpTtsEngine()
        }
    }

    @TaskAction
    fun execute() {
        val deckDir = resolveDeckDir()
        val scriptDir = resolveScriptDir()

        val deckFiles = deckDir.listFiles { f -> f.name.endsWith("-deck.html") }?.toList()
            ?: emptyList()
        val scripts = CapsuleManager.readScriptFiles(scriptDir)

        if (deckFiles.isEmpty()) {
            logger.warn("No *-deck.html files found in {}", deckDir.absolutePath)
            logger.warn("Run 'asciidoctorRevealJs' from slider-gradle first.")
            return
        }

        if (scripts.isEmpty()) {
            logger.warn("No capsule scripts found. Run 'asciidocCapsule' from slider-gradle first.")
            return
        }

        val outDir = project.layout.buildDirectory.dir(
            capsuleExtension.outputDir.get()
        ).get().asFile
        outDir.mkdirs()

        val engine = resolveTtsEngine()

        for (script in scripts) {
            val parsed = CapsuleManager.parseScript(script)
            val audioDir = outDir.resolve(parsed.deckName)
            audioDir.mkdirs()

            for (seg in parsed.slides) {
                val idx = String.format("%02d", seg.index)
                val ttsFile = audioDir.resolve("slide-$idx.mp3")
                if (!ttsFile.exists()) {
                    try {
                        engine.synthesize(seg.speakerNote, ttsFile)
                        logger.lifecycle("  TTS → {} ({} chars)", ttsFile.name, seg.speakerNote.length)
                    } catch (e: TtsException) {
                        logger.warn("  TTS SKIP slide {}: {}", seg.index, e.message)
                    }
                }
            }

            val deckFile = deckFiles.find { it.nameWithoutExtension.startsWith(parsed.deckName) }
                ?: deckFiles.firstOrNull()
            if (deckFile == null) {
                logger.warn("No matching deck HTML found for '{}'", parsed.deckName)
                continue
            }

            val modifiedDeck = injectAudio(deckFile, parsed, audioDir)
            val videoOutputDir = outDir.resolve(parsed.deckName).resolve("video")
            videoOutputDir.mkdirs()

            val deckCapture = resolvePlaywrightCapture()
            try {
                deckCapture.capture(
                    deckHtmlPath = modifiedDeck.absolutePath,
                    outputDir = videoOutputDir,
                    viewportWidth = capsuleExtension.viewportWidth.get(),
                    viewportHeight = capsuleExtension.viewportHeight.get(),
                    slideCount = parsed.slides.size
                )
                deckCapture.close()

                val generatedVideo = videoOutputDir.listFiles { f -> f.name.endsWith(".webm") }
                    ?.firstOrNull()
                if (generatedVideo != null) {
                    val finalVideo = outDir.resolve("${parsed.deckName}.webm")
                    generatedVideo.copyTo(finalVideo, overwrite = true)
                    logger.lifecycle("CAPSULE → {}", finalVideo.absolutePath)
                } else {
                    logger.warn("No video generated by Playwright capture for '{}'", parsed.deckName)
                }
            } catch (e: CapturingException) {
                logger.error("Playwright capture failed for '{}': {}", parsed.deckName, e.message)
                throw e
            }
        }
    }

    private fun injectAudio(deckFile: File, script: CapsuleScript, audioDir: File): File {
        val originalHtml = deckFile.readText()
        val injectedDir = project.layout.buildDirectory.dir("capsule/injected").get().asFile
        injectedDir.mkdirs()

        val hasDataCapsuleSlide = originalHtml.contains("data-capsule-slide=")

        val injectedHtml = originalHtml.lines().map { line ->
            if (line.contains("<section") && !line.contains("</section>")) {
                var mutableLine = line
                for (seg in script.slides) {
                    if (line.contains("data-capsule-slide=\"${seg.index}\"") || line.contains("data-capsule-slide='${seg.index}'")) {
                        val idx = String.format("%02d", seg.index)
                        val audioPath = audioDir.resolve("slide-$idx.mp3").absolutePath
                        mutableLine = mutableLine.replace(
                            "<section",
                            "<section data-audio=\"file://$audioPath\""
                        )
                        break
                    }
                }
                mutableLine
            } else {
                line
            }
        }.joinToString("\n")

        if (!hasDataCapsuleSlide) {
            return injectAudioSequentialFallback(deckFile, script, audioDir, injectedDir)
        }

        val audioScriptInject = """
<!-- CAPSULE-GRADLE: Autoplay audio injection -->
<script>
(function() {
  var currentAudio = null;
  var currentIndex = -1;
  var sections = document.querySelectorAll('.reveal .slides section[data-audio]');
  var audios = [];
  sections.forEach(function(sec) {
    var src = sec.getAttribute('data-audio');
    if (src) {
      var audio = new Audio(src.replace('file://', ''));
      audio.id = 'audio-' + audios.length;
      document.body.appendChild(audio);
      audios.push(audio);
    }
  });
  function playSlideAudio(idx) {
    if (currentAudio) { currentAudio.pause(); currentAudio.currentTime = 0; }
    currentAudio = audios[idx];
    if (currentAudio) {
      currentAudio.currentTime = 0;
      currentAudio.play().catch(function(e) { console.warn('Audio play failed:', e); });
    }
  }
  if (typeof Reveal !== 'undefined') {
    Reveal.on('slidechanged', function(event) {
      playSlideAudio(event.indexh);
    });
    if (audios.length > 0) playSlideAudio(0);
  }
})();
</script>
"""

        val injected = injectedHtml.replace(
            "</body>",
            "$audioScriptInject</body>"
        )

        val outFile = injectedDir.resolve(deckFile.name)
        outFile.writeText(injected)
        return outFile
    }

    private fun injectAudioSequentialFallback(
        deckFile: File,
        script: CapsuleScript,
        audioDir: File,
        injectedDir: File
    ): File {
        val originalHtml = deckFile.readText()

        val slideRegex = Regex("""<section\b[^>]*>""")
        val sections = slideRegex.findAll(originalHtml).toList()

        val injectedHtml = buildString {
            var lastEnd = 0
            var slideIdx = 0
            for (match in sections) {
                append(originalHtml.substring(lastEnd, match.range.first))
                var tag = match.value
                if (slideIdx < script.slides.size) {
                    val seg = script.slides[slideIdx]
                    val idx = String.format("%02d", seg.index)
                    val audioPath = audioDir.resolve("slide-$idx.mp3").absolutePath
                    tag = tag.replace("<section", "<section data-audio=\"file://$audioPath\"")
                }
                append(tag)
                lastEnd = match.range.last + 1
                slideIdx++
            }
            append(originalHtml.substring(lastEnd))
        }

        val audioScriptInject = """
<!-- CAPSULE-GRADLE: Autoplay audio injection (sequential fallback) -->
<script>
(function() {
  var currentAudio = null;
  var sections = document.querySelectorAll('.reveal .slides section[data-audio]');
  var audios = [];
  sections.forEach(function(sec) {
    var src = sec.getAttribute('data-audio');
    if (src) {
      var audio = new Audio(src.replace('file://', ''));
      audio.id = 'audio-' + audios.length;
      document.body.appendChild(audio);
      audios.push(audio);
    }
  });
  function playSlideAudio(idx) {
    if (currentAudio) { currentAudio.pause(); currentAudio.currentTime = 0; }
    currentAudio = audios[idx];
    if (currentAudio) {
      currentAudio.currentTime = 0;
      currentAudio.play().catch(function(e) { console.warn('Audio play failed:', e); });
    }
  }
  if (typeof Reveal !== 'undefined') {
    Reveal.on('slidechanged', function(event) {
      playSlideAudio(event.indexh);
    });
    if (audios.length > 0) playSlideAudio(0);
  }
})();
</script>
"""

        val injected = injectedHtml.replace(
            "</body>",
            "$audioScriptInject</body>"
        )

        val outFile = injectedDir.resolve(deckFile.name)
        outFile.writeText(injected)
        return outFile
    }

    private fun resolveDeckDir(): File {
        val configured = capsuleExtension.deckSourceDir.get()
        val candidate = project.layout.buildDirectory.dir(configured).get().asFile
        if (candidate.exists()) return candidate

        val sliderOutput = project.rootProject.projectDir.parentFile
            ?.resolve("slider-plugin")
            ?.resolve("slider")
            ?.resolve("build")
            ?.resolve("docs")
            ?.resolve("asciidocRevealJs")
        if (sliderOutput != null && sliderOutput.exists()) return sliderOutput

        return candidate
    }

    private fun resolveScriptDir(): File {
        val configured = capsuleExtension.sliderScriptDir.get()
        val candidate = project.layout.buildDirectory.dir(configured).get().asFile
        if (candidate.exists()) return candidate

        val sliderOutput = project.rootProject.projectDir.parentFile
            ?.resolve("slider-plugin")
            ?.resolve("slider")
            ?.resolve("build")
            ?.resolve("capsule")
        if (sliderOutput != null && sliderOutput.exists()) return sliderOutput

        return candidate
    }
}
