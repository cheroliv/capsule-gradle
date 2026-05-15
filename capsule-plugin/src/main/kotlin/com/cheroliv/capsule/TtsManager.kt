package com.cheroliv.capsule

import java.io.File

interface TtsEngine {
    fun synthesize(text: String, outputFile: File)
    fun isAvailable(): Boolean
    fun name(): String
}

class PiperTtsEngine(
    private val executablePath: String = "piper",
    private val model: String = "fr_FR-siwis-medium"
) : TtsEngine {

    override fun isAvailable(): Boolean {
        return try {
            val proc = ProcessBuilder(executablePath, "--help")
                .redirectErrorStream(true)
                .start()
            proc.waitFor()
            proc.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }

    override fun name(): String = "piper"

    override fun synthesize(text: String, outputFile: File) {
        if (!isAvailable()) {
            throw TtsException("Piper executable not found at: $executablePath")
        }

        val wavFile = File(outputFile.parentFile, outputFile.nameWithoutExtension + ".wav")

        val args = listOf(
            executablePath,
            "--model", model,
            "--output_file", wavFile.absolutePath
        )

        val process = ProcessBuilder(args)
            .redirectErrorStream(true)
            .start()

        process.outputStream.bufferedWriter().use { writer ->
            writer.write(text)
            writer.flush()
        }

        val exitCode = process.waitFor()
        if (exitCode != 0) {
            val errorOutput = process.inputStream.bufferedReader().readText()
            throw TtsException("Piper exited with code $exitCode: $errorOutput")
        }

        wavToMp3(wavFile, outputFile)
        wavFile.delete()
    }

    private fun wavToMp3(wavFile: File, mp3File: File) {
        try {
            val proc = ProcessBuilder(
                "ffmpeg", "-y",
                "-i", wavFile.absolutePath,
                "-codec:a", "libmp3lame",
                "-qscale:a", "2",
                mp3File.absolutePath
            ).redirectErrorStream(true).start()

            val exitCode = proc.waitFor()
            if (exitCode != 0) {
                // ffmpeg not available — just rename wav
                wavFile.copyTo(mp3File, overwrite = true)
            }
        } catch (e: Exception) {
            // ffmpeg not available — just rename wav
            wavFile.copyTo(mp3File, overwrite = true)
        }
    }
}

class NoOpTtsEngine : TtsEngine {
    override fun isAvailable(): Boolean = true
    override fun name(): String = "noop"

    override fun synthesize(text: String, outputFile: File) {
        outputFile.parentFile.mkdirs()
        val placeholder = listOf(
            "# TTS PLACEHOLDER (noop engine)",
            "# Text: ${text.take(100)}..."
        ).joinToString("\n")
        outputFile.writeText(placeholder)
    }
}

class TtsException(message: String) : RuntimeException(message)
