package dev.reflux.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.reflux.core.input.Key
import dev.reflux.core.model.Availability
import dev.reflux.core.model.MediaId
import dev.reflux.core.playback.DeviceCapabilities
import dev.reflux.core.playback.VideoCodec
import dev.reflux.core.playback.VideoDecoderSupport
import dev.reflux.core.source.MediaSource
import dev.reflux.core.source.PlaybackTarget
import dev.reflux.core.versions.SelectionCriterion
import dev.reflux.library.pinVersion
import dev.reflux.library.pinnedVersions
import dev.reflux.ui.state.Destination
import dev.reflux.ui.state.detailsModel
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * An episode's versions: offered from its row only where it has more than one, listed on its own page as a
 * film's are, chosen, pinned, and given back; by pointer, on a phone, and with a remote alone (DECISIONS.md,
 * 2026-10-18, "An episode's versions").
 */
@OptIn(ExperimentalTestApi::class)
class EpisodeVersionsUiTest {
    /** The fixture library, with the pilot of Breaking Bad in a second copy: 4K HDR beside the 1080p one. */
    private val world = populated().also {
        it.file("Breaking Bad/Season 1/Breaking.Bad.S01E01.2160p.HDR.x265.mkv")
        runBlocking { it.pipeline.refresh() }
    }
    private val show = world.show("Breaking Bad").item.id
    private val episodes = world.episodes("Breaking Bad").map { it.item.id }
    private val pilot = episodes.first()
    private fun selection(device: DeviceCapabilities = testDevice) = world.library.selectVersion(pilot, device)
    private fun ofHeight(height: Int) = world.library.versions(pilot).single { it.version.stream.video?.nominalHeight == height }.version.id

    private fun androidx.compose.ui.test.DesktopComposeUiTest.has(text: String) = onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun onlyAnEpisodeWithSeveralVersionsOffersThem() {
        val model = assertNotNull(world.library.detailsModel(show, testDevice))
        assertEquals(mapOf(pilot to 2), model.seasons.first().versions, "the pilot alone has something to choose between")
        assertTrue(model.seasons.drop(1).all { it.versions.isEmpty() })

        screen(world) { app ->
            app.navigator.open(Destination.Details(show))
            awaitText("2 versions")
            assertEquals(1, onAllNodesWithText("2 versions").fetchSemanticsNodes().size)
            save("episode-versions-show")
            // A season where no episode has another version keeps its rows as they were.
            onNodeWithText("Season 2").performClick()
            awaitText("Seven Thirty-Seven", substring = true)
            assertTrue(!has("2 versions") && !has("versions"))
        }
    }

    @Test
    fun anEpisodesVersionIsChosenPinnedAndGivenBack() = screen(world) { app ->
        app.navigator.open(Destination.Details(show))
        awaitText("2 versions")
        onNodeWithText("2 versions").performScrollTo().performClick()
        awaitText("Versions")
        // Its own page, under its show's name, with what tells it apart.
        assertTrue(has("S1 · E1 · Pilot"), "the episode says which it is")
        assertTrue(has("Recommended"))
        assertEquals(ofHeight(2160), selection().best?.version?.id)
        assertTrue(has("All episodes") && !has("Identify"), "an episode is identified through its show")
        save("episode-versions-page")

        // The 1080p copy, made the one Play always uses.
        onAllNodesWithText("Always play this").onLast().performScrollTo().performClick()
        await("the choice to be shown", { world.library.pinnedVersions(pilot) }) { has("Play always uses this one") }
        assertEquals(setOf(ofHeight(1080)), world.library.pinnedVersions(pilot))
        assertEquals(SelectionCriterion.USER_PREFERENCE, selection().decidedBy)
        assertEquals(emptySet(), world.library.pinnedVersions(episodes[1]), "the pin is this episode's")
        awaitText("Play will always use this version.")
        save("episode-versions-pinned")

        // Playing it from the show's page plays the pinned copy.
        assertTrue(app.navigator.back())
        awaitText("2 versions")
        onNode(hasText("Pilot", substring = true) and hasClickAction()).performScrollTo().performClick()
        await("playback of the pinned copy", { app.playback }) { app.playback != null }
        app.playback?.close()

        // And given back.
        app.navigator.open(Destination.Details(pilot))
        awaitText("Let Reflux choose")
        onNodeWithText("Let Reflux choose").performScrollTo().performClick()
        await("Reflux's choice again") { has("Recommended") && world.library.pinnedVersions(pilot).isEmpty() }
        assertEquals(ofHeight(2160), selection().best?.version?.id)
    }

    @Test
    fun theVersionsAreBeneathTheEpisodeOnAPhone() = screen(world, width = 412, height = 915) { app ->
        app.navigator.open(Destination.Details(show))
        awaitText("2 versions")
        val choice = onNodeWithText("2 versions").performScrollTo()
        val bounds = choice.fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.left >= 0f && bounds.right <= 412f, "within the screen: $bounds")
        val row = onNode(hasText("Pilot", substring = true) and hasClickAction()).fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.top >= row.bottom, "beneath the episode, not over it: $bounds under $row")
        save("episode-versions-phone")
        choice.performClick()
        awaitText("Versions")
        onAllNodesWithText("Always play this").onFirst().performScrollTo().performClick()
        await("the choice to be kept") { world.library.pinnedVersions(pilot).isNotEmpty() }
        save("episode-versions-pinned-phone")
    }

    @Test
    fun theRemoteReachesAnEpisodesVersionsAndComesBack() = television(world) { app ->
        app.navigator.open(Destination.Details(show))
        await("focus on Play") { onAllNodes(isFocused() and hasText("Continue", substring = true)).fetchSemanticsNodes().isNotEmpty() || onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty() }
        // Down to the pilot's row, then a step to the right to its versions.
        fun focused() = onAllNodes(isFocused()).fetchSemanticsNodes().map { it.config.toString() }
        var steps = 0
        while (onAllNodes(isFocused() and hasText("Pilot", substring = true)).fetchSemanticsNodes().isEmpty() && steps++ < 6) remote(app, Key.ARROW_DOWN)
        await("focus on the pilot", ::focused) { onAllNodes(isFocused() and hasText("Pilot", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        remote(app, Key.ARROW_RIGHT)
        await("focus on its versions", ::focused) { onAllNodes(isFocused() and hasText("2 versions")).fetchSemanticsNodes().isNotEmpty() }

        // The two never share their room: the focused choice stays clear of the episode's own row and text.
        val row = onNode(hasText("Pilot", substring = true) and hasClickAction()).fetchSemanticsNode().boundsInRoot
        val choice = onNode(hasText("2 versions") and hasClickAction()).fetchSemanticsNode().boundsInRoot
        assertTrue(choice.left - row.right >= 20f, "a gap for the focus ring between $row and $choice")
        save("episode-versions-tv-row")

        remote(app, Key.DPAD_CENTER)
        awaitText("Versions")
        await("focus on Play", ::focused) { onAllNodes(isFocused() and hasText("Play")).fetchSemanticsNodes().isNotEmpty() }
        // Down to the first version (the recommended 4K), then down to the 1080p one and across to its choice.
        remote(app, Key.ARROW_DOWN, times = 2)
        remote(app, Key.ARROW_RIGHT)
        await("focus on the choice", ::focused) { onAllNodes(isFocused() and hasText("Always play this")).fetchSemanticsNodes().isNotEmpty() }
        save("episode-versions-tv-choice")
        remote(app, Key.DPAD_CENTER)
        await("the choice to be kept") { world.library.pinnedVersions(pilot).isNotEmpty() }
        awaitText("Let Reflux choose")
        // The chosen version moves to the top, and focus goes with it: a second press gives the choice back
        // rather than choosing the version that took its place (seen on the Fire TV).
        await("focus to stay with the chosen version", ::focused) { onAllNodes(isFocused() and hasText("Let Reflux choose")).fetchSemanticsNodes().isNotEmpty() }
        save("episode-versions-tv-pinned")

        // Back is the show's page, where the remote left it.
        remote(app, Key.BACK)
        awaitText("2 versions")
        await("focus back on the versions", ::focused) { onAllNodes(isFocused() and hasText("2 versions")).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(Destination.Details(show), app.navigator.current)
    }

    @Test
    fun backLeavesTheVersionsUnchanged() = television(world) { app ->
        app.navigator.open(Destination.Details(pilot))
        awaitText("Versions")
        remote(app, Key.BACK)
        assertEquals(emptySet(), world.library.pinnedVersions(pilot), "looking is not choosing")
    }

    @Test
    fun aPinnedVersionThatCannotPlayIsSetAsideAndSaidSo() {
        world.library.pinVersion(pilot, ofHeight(2160))
        // Somewhere without HEVC: the pinned 4K copy cannot play, so Best Version falls back to the 1080p one.
        val withoutHevc = testDevice.copy(videoDecoders = listOf(VideoDecoderSupport(VideoCodec.H264, 3840, 2160, 8, hardware = true)))
        val model = assertNotNull(world.library.detailsModel(pilot, withoutHevc))
        assertEquals(ofHeight(1080), model.versions.single { it.best }.ranked.version.id)
        assertTrue(model.versions.single { it.ranked.version.id == ofHeight(2160) }.pinned, "the choice is kept, not rewritten")
        assertEquals("Your choice can't be played on this device, so Play uses the best version here. It stays your choice.", model.pinNote)
        assertEquals(setOf(ofHeight(2160)), world.library.pinnedVersions(pilot))

        // Its source away: set aside, said, and kept.
        val away = object : MediaSource {
            override val descriptor = world.source.descriptor
            override suspend fun availability() = Availability.UNAVAILABLE
            override suspend fun playbackTarget(path: String) = PlaybackTarget(path)
        }
        runBlocking { world.library.refreshAvailability(away) }
        val offline = assertNotNull(world.library.detailsModel(pilot, testDevice), "an episode whose source is away keeps its page")
        assertEquals("Your choice isn't connected right now, so nothing here can play now. It stays your choice.", offline.pinNote)
        assertTrue(offline.versions.all { "Not connected" in it.notes })
        runBlocking { world.library.refreshAvailability(world.source) }
        assertNull(assertNotNull(world.library.detailsModel(pilot, testDevice)).pinNote, "back, the choice is what Play uses")

        screen(world, FakePlatform().apply { device = withoutHevc }) { app ->
            app.navigator.open(Destination.Details(pilot))
            awaitText("Your choice can't be played on this device", substring = true)
            save("episode-versions-pin-set-aside")
        }
    }

    @Test
    fun theLaunchersEpisodeOpensItsOwnPage() {
        // Watch Next names an episode; its page is a real one, not "no longer in your library".
        val model = assertNotNull(world.library.detailsModel(episodes[1], testDevice))
        assertEquals(show, model.showId)
        assertEquals("S1 · E2 · Cat's in the Bag...", model.tagline)
        assertEquals(1, model.versions.size)
        assertTrue(model.versions.none { it.pinnable }, "one version: nothing to pin")
        assertNull(model.pinNote)

        television(world) { app ->
            app.continueFromOutside(episodes[1])
            await("playback from the launcher", { app.playback }) { app.playback != null }
            app.playback?.close()
            await("the episode's page beneath it", { app.navigator.current }) { app.playback == null && has("S1 · E2 · Cat's in the Bag...") }
            assertTrue(!has("This is no longer in your library"))
            assertEquals(Destination.Details(episodes[1]), app.navigator.current)
        }
    }

}

@OptIn(ExperimentalTestApi::class)
private fun androidx.compose.ui.test.DesktopComposeUiTest.awaitText(text: String, substring: Boolean) =
    await("\"$text\" to appear") { onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }
