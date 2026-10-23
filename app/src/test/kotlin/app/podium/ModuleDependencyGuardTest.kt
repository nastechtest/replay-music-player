package app.replay

import org.junit.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * Dependency guard enforcing architectural invariants (architecture.md §4, deployment.md §6).
 *
 * Rules:
 * 1. Feature modules only depend on :sources:api, never on concrete provider implementations.
 * 2. Domain modules stay pure Kotlin / JVM (ADR-012).
 */
class ModuleDependencyGuardTest {

    private val projectRoot: File by lazy {
        var dir: File? = File(".").canonicalFile
        while (dir != null && !File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile
        }
        dir ?: File(".").canonicalFile
    }

    @Test
    fun `feature modules never depend directly on provider implementations`() {
        val featureDir = File(projectRoot, "feature")
        if (!featureDir.exists()) return

        val featureBuildFiles = featureDir.walkTopDown()
            .filter { it.name == "build.gradle.kts" }
            .toList()

        assertTrue(featureBuildFiles.isNotEmpty(), "Feature build files should exist")

        for (buildFile in featureBuildFiles) {
            val content = buildFile.readText()
            assertTrue(
                !content.contains("\":sources:local\""),
                "${buildFile.path} must not depend on :sources:local"
            )
            assertTrue(
                !content.contains("\":sources:youtubemusic\""),
                "${buildFile.path} must not depend on :sources:youtubemusic"
            )
        }
    }

    @Test
    fun `domain modules apply pure jvm library plugin`() {
        val domainModules = listOf(
            "core/model",
            "core/common",
            "core/lyrics",
            "sources/api",
            "player/api"
        )

        for (modulePath in domainModules) {
            val buildFile = File(projectRoot, "$modulePath/build.gradle.kts")
            if (buildFile.exists()) {
                val content = buildFile.readText()
                assertTrue(
                    content.contains("replay.jvm.library"),
                    "${buildFile.path} must apply replay.jvm.library convention plugin"
                )
                assertTrue(
                    !content.contains("replay.android"),
                    "${buildFile.path} must not apply Android convention plugins"
                )
            }
        }
    }
}
