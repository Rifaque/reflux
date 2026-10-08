package dev.reflux.sources.local

import dev.reflux.core.model.Availability
import dev.reflux.core.source.SourceUnavailableException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LocalFolderSourceTest {
    private val root: Path = Files.createTempDirectory("reflux-local")

    @AfterTest
    fun cleanUp() {
        root.toFile().deleteRecursively()
    }

    private fun file(relative: String, content: String = "x"): Path =
        root.resolve(relative).also { it.parent.createDirectories(); it.writeText(content) }

    @Test
    fun listsRelevantFilesDeterministically() = runTest {
        file("Movies/Heat (1995)/Heat (1995).mkv", "12345")
        file("Movies/Heat (1995)/poster.jpg")
        file("Movies/Heat (1995)/Heat (1995).en.srt")
        file("Movies/Heat (1995)/Heat (1995).nfo")
        file("Movies/.hidden/Secret.mkv")
        file("Movies/@eaDir/thumb.mkv")
        file("Movies/Alien (1979).mkv")

        val files = LocalFolderSource(root).files().toList()
        assertEquals(
            listOf(
                "Movies/Alien (1979).mkv",
                "Movies/Heat (1995)/Heat (1995).en.srt",
                "Movies/Heat (1995)/Heat (1995).mkv",
                "Movies/Heat (1995)/poster.jpg",
            ),
            files.map { it.path },
        )
        assertEquals(5, files.single { it.path.endsWith("Heat (1995).mkv") }.sizeBytes)
    }

    @Test
    fun followsSymlinksWithoutLooping() = runTest {
        file("real/Show/S01E01.mkv")
        Files.createSymbolicLink(root.resolve("real/Show/loop"), root.resolve("real"))
        Files.createSymbolicLink(root.resolve("linked"), root.resolve("real/Show"))
        val paths = LocalFolderSource(root).files().toList().map { it.path }
        // "linked" sorts first, so the directory is reached through it and then not walked again.
        assertEquals(listOf("linked/S01E01.mkv"), paths)
    }

    @Test
    fun missingRootIsUnavailable() = runTest {
        val source = LocalFolderSource(root.resolve("unplugged-drive"))
        assertEquals(Availability.UNAVAILABLE, source.availability())
        assertFailsWith<SourceUnavailableException> { source.files().toList() }
    }

    @Test
    fun playbackTargetsAreFileUrisInsideTheRoot() = runTest {
        file("a b/movie.mkv")
        val source = LocalFolderSource(root)
        val target = source.playbackTarget("a b/movie.mkv")
        assertTrue(target.uri.startsWith("file:"))
        assertTrue(target.uri.endsWith("a%20b/movie.mkv"))
        assertFailsWith<IllegalArgumentException> { source.resolve("../escape.mkv") }
    }

    @Test
    fun sourceIdIsStableForTheSameFolder() {
        assertEquals(LocalFolderSource(root).descriptor.id, LocalFolderSource(root.resolve(".")).descriptor.id)
    }
}
