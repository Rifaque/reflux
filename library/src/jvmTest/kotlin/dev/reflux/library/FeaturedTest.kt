package dev.reflux.library

import dev.reflux.core.model.ArtworkKind
import dev.reflux.core.model.ArtworkLocator
import dev.reflux.core.model.Availability
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.Movie
import dev.reflux.core.model.WatchState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The featured hero (B-014): what the viewer is in the middle of, else a daily pick of the best unwatched, never by title. */
class FeaturedTest {
    private fun movie(title: String, id: String = "m-${title.hashCode().toUInt()}", backdrop: Boolean = true, watched: Boolean = false) = LibraryEntry(
        item = Movie(MediaId(id), title, 2000),
        artwork = if (backdrop) mapOf(ArtworkKind.BACKDROP to ArtworkLocator.Remote("https://img/$id.jpg")) else emptyMap(),
        watchState = if (watched) WatchState(MediaId(id), completed = true) else null,
        availability = Availability.AVAILABLE,
        favorite = false,
    )

    // The tester's library began with "2 Fast 2 Furious": alphabetical order is what must not decide.
    private val library = listOf("2 Fast 2 Furious", "Alien", "Blade Runner", "Casablanca", "Dune", "Heat", "Jaws", "Memento", "Psycho", "Rocky", "Se7en", "Up")
        .map { movie(it) }

    @Test
    fun whatTheViewerIsInTheMiddleOfComesFirst() {
        val halfway = movie("Zodiac")
        assertEquals(halfway, Featured.choose(listOf(halfway), library, day = 1, viewer = "main"))
        // One without a backdrop gives way to the next thing to continue, then to the daily pick.
        assertEquals(halfway, Featured.choose(listOf(movie("Bare", backdrop = false), halfway), library, day = 1, viewer = "main"))
    }

    @Test
    fun thePickHoldsThroughADayAndChangesFromDayToDay_neverAlphabetically() {
        val picks = (0L until 30L).map { day -> Featured.choose(emptyList(), library, day, "main")!!.item.title }
        assertEquals(picks, (0L until 30L).map { day -> Featured.choose(emptyList(), library.shuffled(), day, "main")!!.item.title }, "the order things are listed in decides nothing")
        assertTrue(picks.toSet().size >= 6, "a month shows many works: $picks")
        assertTrue(picks.count { it == "2 Fast 2 Furious" } < 8, "the alphabetically first is no more likely than any other: $picks")
        assertEquals(Featured.choose(emptyList(), library, 7, "main"), Featured.choose(emptyList(), library, 7, "main"))
    }

    @Test
    fun twoViewersDoNotSeeTheSamePickEveryDay() {
        val differs = (0L until 30L).count { day -> Featured.choose(emptyList(), library, day, "main") != Featured.choose(emptyList(), library, day, "sam") }
        assertTrue(differs > 10, "two profiles' heroes differ on most days: $differs of 30")
    }

    @Test
    fun watchedWorksStepAsideWhileAnythingIsUnwatched() {
        val watched = library.map { movie(it.item.title, it.item.id.value, watched = it.item.title != "Heat") }
        repeat(10) { day -> assertEquals("Heat", Featured.choose(emptyList(), watched, day.toLong(), "main")!!.item.title) }
        val allWatched = library.map { movie(it.item.title, it.item.id.value, watched = true) }
        assertNotEquals(null, Featured.choose(emptyList(), allWatched, 3, "main"), "with nothing unwatched, the rest are still featured")
    }

    @Test
    fun withoutABackdropNothingIsFeatured() {
        assertNull(Featured.choose(emptyList(), library.map { movie(it.item.title, it.item.id.value, backdrop = false) }, 1, "main"))
        assertNull(Featured.choose(emptyList(), emptyList(), 1, "main"))
    }
}
