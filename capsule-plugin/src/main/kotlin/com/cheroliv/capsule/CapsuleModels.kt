package com.cheroliv.capsule

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

data class SlideSegment(
    val index: Int,
    val title: String,
    val speakerNote: String
)

data class CapsuleScript(
    val deckName: String,
    val slides: List<SlideSegment>
)

open class CapsuleExtension @Inject constructor(objects: ObjectFactory) {
    val ttsEngine: Property<String> = objects.property(String::class.java)
        .convention("piper")

    val ttsVoice: Property<String> = objects.property(String::class.java)
        .convention("fr_FR-siwis-medium")

    val piperExecutablePath: Property<String> = objects.property(String::class.java)
        .convention("piper")

    val ttsFallbackEnabled: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(true)

    val outputDir: Property<String> = objects.property(String::class.java)
        .convention("build/capsule")

    val sliderScriptDir: Property<String> = objects.property(String::class.java)
        .convention("build/capsule")

    val viewportWidth: Property<Int> = objects.property(Int::class.java)
        .convention(1408)

    val viewportHeight: Property<Int> = objects.property(Int::class.java)
        .convention(792)

    val playwrightTimeout: Property<Double> = objects.property(Double::class.java)
        .convention(120_000.0)

    val chromiumExecutablePath: Property<String> = objects.property(String::class.java)
        .convention("")

    val deckSourceDir: Property<String> = objects.property(String::class.java)
        .convention("build/docs/asciidocRevealJs")
}
