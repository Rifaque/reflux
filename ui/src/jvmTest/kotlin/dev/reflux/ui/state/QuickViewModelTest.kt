package dev.reflux.ui.state

import dev.reflux.ui.TestWorld
import dev.reflux.ui.testDevice
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Media Quick View's summary (CACHY-003): it no longer reads a show's whole page, every episode's entry included, so
 * it must still say exactly what that page says of the work: its title, facts, genres, words, and main button.
 */
class QuickViewModelTest {
    private fun QuickViewModel.assertSays(details: DetailsModel) {
        assertEquals(details.card.id, card.id)
        assertEquals(details.card.title, card.title)
        assertEquals(details.card.poster, card.poster)
        assertEquals(details.facts, facts)
        assertEquals(details.genres, genres)
        assertEquals(details.overview, overview)
        assertEquals(details.tagline, tagline)
        assertEquals(details.playId, playId)
        assertEquals(details.playLabel, playLabel)
    }

    @Test
    fun saysWhatTheDetailsPageSaysOfEveryWork() = runBlocking {
        val world = TestWorld()
        world.populate()
        val library = world.library
        val works = library.movies() + library.shows()
        for (work in works) {
            val details = assertNotNull(library.detailsModel(work.item.id, testDevice), work.item.title)
            assertNotNull(library.quickViewModel(work.item.id, testDevice), work.item.title).assertSays(details)
        }
    }

    @Test
    fun aStartedShowContinuesWhereTheDetailsPageDoes() = runBlocking {
        val world = TestWorld()
        world.populate()
        val library = world.library
        val show = world.show("Breaking Bad").item.id
        assertEquals("Play S1 · E1", library.quickViewModel(show, testDevice)?.playLabel?.replace("E01", "E1"))
        // Watched the first two and part of the third: both say Continue, at the same episode.
        val episodes = world.episodes("Breaking Bad")
        library.setWatched(episodes[0].item.id, true)
        world.clock += 1_000
        library.setWatched(episodes[1].item.id, true)
        world.clock += 1_000
        library.progress(episodes[2].item.id, 600_000, 2_700_000)
        val details = assertNotNull(library.detailsModel(show, testDevice))
        val quick = assertNotNull(library.quickViewModel(show, testDevice))
        quick.assertSays(details)
        assertEquals(episodes[2].item.id, quick.playId)
        assert(quick.playLabel.startsWith("Continue ")) { quick.playLabel }
    }

    @Test
    fun anEpisodeAndAnUnknownWorkAreAsTheirPages() = runBlocking {
        val world = TestWorld()
        world.populate()
        val library = world.library
        val episode = world.episodes("Severance").first().item.id
        assertNotNull(library.quickViewModel(episode, testDevice)).assertSays(assertNotNull(library.detailsModel(episode, testDevice)))
        assertNull(library.quickViewModel(dev.reflux.core.model.MediaId("not-in-the-library"), testDevice))
    }
}
