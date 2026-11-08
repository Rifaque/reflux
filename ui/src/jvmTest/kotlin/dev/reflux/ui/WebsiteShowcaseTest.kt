package dev.reflux.ui

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import dev.reflux.core.metadata.Credit
import dev.reflux.core.metadata.CreditRole
import dev.reflux.core.metadata.MetadataCandidate
import dev.reflux.core.metadata.MetadataKind
import dev.reflux.core.metadata.MetadataProvider
import dev.reflux.core.metadata.MetadataQuery
import dev.reflux.core.metadata.ProviderAttribution
import dev.reflux.core.metadata.ProviderRef
import dev.reflux.core.metadata.RemoteArtwork
import dev.reflux.core.metadata.SeasonMetadata
import dev.reflux.core.metadata.WorkMetadata
import dev.reflux.core.model.ArtworkKind
import dev.reflux.core.model.CalendarDate
import dev.reflux.ui.state.Destination
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.Test

/**
 * The website's product images (docs/WEBSITE.md, "Product images"): the real experience layer, rendered offscreen
 * over a library of the Blender Foundation's open films with their own Creative Commons artwork, never commercial
 * titles. Runs only when asked:
 *
 *     ./gradlew :ui:jvmTest --tests '*WebsiteShowcaseTest*' -Preflux.showcase=<dir>
 *
 * with the artwork in `<dir>/art` (website/showcase/README.md lists each file, its source, and its license); writes
 * PNGs to `<dir>/out`.
 */
@OptIn(ExperimentalTestApi::class)
class WebsiteShowcaseTest {
    private val dir = System.getProperty("reflux.showcase")?.takeIf { it.isNotBlank() }?.let(::File)

    private class Film(val key: String, val title: String, val year: Int, val director: String, val minutes: Int, val genres: List<String>, val overview: String)

    private val films = listOf(
        Film("spring", "Spring", 2019, "Andy Goralczyk", 8, listOf("Animation", "Fantasy"), "A shepherd girl and her dog face ancient spirits to keep the cycle of life turning."),
        Film("charge", "Charge", 2022, "Hjalti Hjálmarsson", 4, listOf("Animation", "Action"), "In a frozen, energy-starved city, an old man risks everything to steal a single charge."),
        Film("coffee-run", "Coffee Run", 2020, "Hjalti Hjálmarsson", 3, listOf("Animation", "Drama"), "Fuelled by caffeine, a young woman runs through the bittersweet memories of her past relationship."),
        Film("sintel", "Sintel", 2010, "Colin Levy", 15, listOf("Animation", "Fantasy"), "A lonely young woman searches the world for the baby dragon she raised, after it is taken from her."),
        Film("tears-of-steel", "Tears of Steel", 2012, "Ian Hubert", 12, listOf("Science Fiction"), "In a future Amsterdam, scientists and warriors stage a desperate plan to save the world from destructive robots."),
        Film("cosmos-laundromat", "Cosmos Laundromat", 2015, "Mathieu Auvray", 12, listOf("Animation", "Comedy"), "On a desolate island, a weary sheep meets a mysterious salesman who offers him the gift of a lifetime."),
        Film("sprite-fright", "Sprite Fright", 2021, "Matthew Luhn", 10, listOf("Animation", "Horror", "Comedy"), "Rowdy teenagers on a woodland outing meet a band of mushroom sprites who do not take kindly to visitors."),
        Film("big-buck-bunny", "Big Buck Bunny", 2008, "Sacha Goedegebure", 10, listOf("Animation", "Comedy"), "A large and gentle rabbit gets even with three bullies who spoil his peaceful day in the meadow."),
        Film("elephants-dream", "Elephants Dream", 2006, "Bassam Kurdali", 11, listOf("Animation", "Science Fiction"), "Two men explore an endless, ever-changing machine, and each sees something different in it."),
    )

    /** The films as a catalog: each has its own description, credits, and the artwork in `<dir>/art`. */
    private val catalog = object : MetadataProvider {
        override val id = "showcase"
        override val attribution: ProviderAttribution? = null
        private fun ref(film: Film) = ProviderRef(id, MetadataKind.MOVIE, film.key)
        private fun norm(t: String) = t.lowercase().filter(Char::isLetterOrDigit)
        override suspend fun search(query: MetadataQuery) = films.filter { norm(it.title) == norm(query.title) }
            .map { MetadataCandidate(ref(it), it.title, year = it.year, overview = it.overview, popularity = 50.0) }
        override suspend fun findByExternalId(kind: MetadataKind, source: String, id: String): ProviderRef? = null
        override suspend fun season(ref: ProviderRef, seasonNumber: Int, language: String): SeasonMetadata? = null
        override suspend fun details(ref: ProviderRef, language: String): WorkMetadata? {
            val film = films.firstOrNull { ref(it) == ref } ?: return null
            return WorkMetadata(
                ref = ref, title = film.title, overview = film.overview, genres = film.genres, runtimeMinutes = film.minutes,
                releaseDate = CalendarDate(film.year, 4, 10), contentRating = null,
                credits = listOf(Credit(film.director, CreditRole.DIRECTOR)),
                artwork = listOfNotNull(
                    RemoteArtwork(ArtworkKind.POSTER, "art://poster-${film.key}").takeIf { art("poster-${film.key}") != null },
                    RemoteArtwork(ArtworkKind.BACKDROP, "art://backdrop-${film.key}").takeIf { art("backdrop-${film.key}") != null },
                ),
            )
        }
    }

    private fun art(name: String): File? = File(dir, "art").listFiles()?.firstOrNull { it.nameWithoutExtension == name }

    private val worlds = mutableListOf<TestWorld>()

    @org.junit.After
    fun deleteWorlds() = worlds.forEach { it.delete() }

    private fun world() = TestWorld(catalog = catalog).also { world ->
        worlds += world
        runBlocking {
            for (film in films) world.file("${film.title} (${film.year})/${film.title.replace(' ', '.')}.${film.year}.1080p.mkv", when (film.key) { "sintel" -> 1_870_000_000; "sprite-fright" -> 1_310_000_000; else -> 8 })
            // Two copies of one film: what Best Version chooses between.
            world.file("Sintel (2010)/Sintel.2010.2160p.HDR.x265.mkv", 6_240_000_000)
            world.pipeline.refresh()
        }
        val id = { title: String -> world.movie(title).item.id }
        world.library.stopped(id("Charge"), 2 * 60_000L, 4 * 60_000L)
        world.clock += 60_000
        world.library.stopped(id("Tears of Steel"), 7 * 60_000L, 12 * 60_000L)
        world.clock += 60_000
        world.library.stopped(id("Coffee Run"), 60_000L, 3 * 60_000L)
        world.library.setFavorite(id("Sintel"), true)
    }

    private fun platform(television: Boolean = false) = FakePlatform(
        television = television,
        artworkFiles = File(dir, "art").listFiles().orEmpty().associateBy { "art://" + it.nameWithoutExtension },
    )

    private fun DesktopComposeUiTest.shoot(name: String) {
        waitForIdle()
        Thread.sleep(400) // artwork decodes off the UI thread
        waitForIdle()
        val png = Image.makeFromBitmap(captureToImage().asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)!!
        File(dir, "out").apply { mkdirs() }.resolve("$name.png").writeBytes(png.bytes)
    }

    @Test
    fun theWebsitesProductImages() {
        assumeTrue("-Preflux.showcase=<dir> renders the website's images", dir != null)
        // Desktop: Home, and a film's page with its two versions.
        screen(world(), platform(), width = 1440, height = 900, density = 2f) { app ->
            awaitText("Continue Watching")
            shoot("home-desktop")
            app.navigator.open(Destination.Details(app.library.movies().first { it.item.title == "Sintel" }.item.id))
            awaitText("A lonely young woman searches the world for the baby dragon she raised, after it is taken from her.")
            shoot("details-desktop")
            app.navigator.show(Destination.Movies)
            awaitText("Big Buck Bunny")
            shoot("movies-desktop")
        }
        // A television, by remote: 1920 × 1080 at 2×, as Google TV and Fire TV lay it out.
        screen(world(), platform(television = true), width = 960, height = 540, density = 2f) { app ->
            awaitText("Continue Watching")
            shoot("home-tv")
            app.navigator.show(Destination.Movies)
            awaitText("Big Buck Bunny")
            shoot("movies-tv")
        }
        // A phone held upright, and a tablet on its side.
        screen(world(), platform(), width = 412, height = 915, density = 2.625f) {
            awaitText("Continue Watching")
            shoot("home-phone")
        }
        screen(world(), platform(), width = 1164, height = 777, density = 2f) { app ->
            awaitText("Continue Watching")
            shoot("home-tablet")
            app.navigator.open(Destination.Details(app.library.movies().first { it.item.title == "Sprite Fright" }.item.id))
            awaitText("Rowdy teenagers on a woodland outing meet a band of mushroom sprites who do not take kindly to visitors.")
            shoot("details-tablet")
        }
    }
}
