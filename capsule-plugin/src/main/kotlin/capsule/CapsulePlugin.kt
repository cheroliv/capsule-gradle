package capsule

import org.gradle.api.Plugin
import org.gradle.api.Project

class CapsulePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        try {
            project.plugins.apply("education.cccp.slider")
        } catch (_: Exception) {
            project.logger.debug("slider-gradle not on classpath, skipping auto-apply")
        }
        project.extensions.create("capsule", CapsuleExtension::class.java)
        CapsuleManager(project).registerTasks()
    }
}
