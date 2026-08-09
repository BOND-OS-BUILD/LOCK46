package com.lock46.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards against the two things that broke the previous LOCK46 codebase: an invalid
 * package name, and build identity drifting between Gradle and the source.
 *
 * These run as ordinary unit tests so CI fails on a regression rather than shipping it.
 */
class RepositoryHygieneTest {

    private val repoRoot: File by lazy {
        // Unit tests run with the module directory as the working directory.
        generateSequence(File("").absoluteFile) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile }
            ?: error("could not locate repository root from ${File("").absolutePath}")
    }

    private fun sourceFiles(): List<File> =
        repoRoot.walkTopDown()
            .onEnter { dir ->
                dir.name !in setOf(".git", "build", ".gradle", ".idea", "node_modules")
            }
            .filter { it.isFile }
            .filter { it.extension in setOf("kt", "kts", "xml", "pro", "toml", "yml", "yaml", "md", "html", "json") }
            .toList()

    @Test
    fun `the invalid in-lock46-app package name appears nowhere`() {
        // "in" is a Kotlin keyword, so a package beginning with it cannot compile.
        val needle = listOf("in", "lock46", "app").joinToString(".")

        val offenders = sourceFiles()
            .filter { it.name != "RepositoryHygieneTest.kt" }
            .filter { it.readText().contains(needle) }
            .map { it.relativeTo(repoRoot).path }

        assertEquals("files still referencing the invalid package: $offenders", emptyList<String>(), offenders)
    }

    @Test
    fun `every kotlin source declares the com-lock46-app package`() {
        val offenders = sourceFiles()
            .filter { it.extension == "kt" }
            .filterNot { it.readText().lineSequence().any { line -> line.startsWith("package com.lock46.app") } }
            .map { it.relativeTo(repoRoot).path }

        assertEquals(emptyList<String>(), offenders)
    }

    @Test
    fun `build identity matches the gradle configuration`() {
        val gradle = File(repoRoot, "android/build.gradle.kts").readText()

        assertTrue(
            "applicationId in build.gradle.kts must match BuildConfigInfo.APPLICATION_ID",
            gradle.contains("applicationId = \"${BuildConfigInfo.APPLICATION_ID}\"")
        )
        assertTrue(
            "versionName in build.gradle.kts must match BuildConfigInfo.VERSION_NAME",
            gradle.contains("versionName = \"${BuildConfigInfo.VERSION_NAME}\"")
        )
        assertTrue(
            "namespace must match the application id",
            gradle.contains("namespace = \"${BuildConfigInfo.APPLICATION_ID}\"")
        )
    }

    @Test
    fun `the app declares no internet permission`() {
        // LOCK46 V1 is offline-first by construction, not by convention.
        val manifest = File(repoRoot, "android/src/main/AndroidManifest.xml").readText()

        listOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.READ_CONTACTS",
            "android.permission.READ_SMS",
            "android.permission.RECORD_AUDIO",
            "android.permission.CAMERA",
            "android.permission.READ_CALL_LOG",
            "android.permission.QUERY_ALL_PACKAGES"
        ).forEach { forbidden ->
            assertTrue(
                "$forbidden must not be declared",
                !manifest.contains(forbidden)
            )
        }
    }

    @Test
    fun `there is exactly one android manifest`() {
        val manifests = sourceFiles().filter { it.name == "AndroidManifest.xml" }
        assertEquals(
            "expected a single manifest, found ${manifests.map { it.relativeTo(repoRoot).path }}",
            1,
            manifests.size
        )
    }

    @Test
    fun `there is exactly one main activity`() {
        // Built at runtime so this test's own source does not count as a match.
        val needle = ": " + "ComponentActivity" + "()"

        val activities = File(repoRoot, "android/src/main")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains(needle) }
            .map { it.relativeTo(repoRoot).path }
            .toList()

        assertEquals("expected a single Activity, found $activities", 1, activities.size)
    }
}
