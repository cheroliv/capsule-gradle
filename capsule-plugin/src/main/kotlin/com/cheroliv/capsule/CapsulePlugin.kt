package com.cheroliv.capsule

import org.gradle.api.Plugin
import org.gradle.api.Project

class CapsulePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.extensions.create("capsule", CapsuleExtension::class.java)
        CapsuleManager(project).registerTasks()
    }
}
