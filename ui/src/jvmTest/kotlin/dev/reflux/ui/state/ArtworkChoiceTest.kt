package dev.reflux.ui.state

import dev.reflux.core.model.ArtworkLocator
import dev.reflux.core.model.MediaLocation
import dev.reflux.core.model.SourceId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArtworkChoiceTest {
    private val poster = ArtworkLocator.Remote("https://img/poster.jpg")
    private val backdrop = ArtworkLocator.Remote("https://img/backdrop.jpg")
    private val phoneHeader = 412f / 560f
    private val desktopHeader = 1440f / 560f

    @Test
    fun eachFrameTakesTheImageMadeForItsShape() {
        assertEquals(listOf(poster, backdrop), ArtworkChoice.portrait(poster, backdrop))
        assertEquals(listOf(backdrop, poster), ArtworkChoice.landscape(backdrop, poster, 16f / 9f))
        // A poster beside the title, its backdrop across the header.
        assertEquals(ArtworkChoice.Header(listOf(backdrop), posterBeside = true), ArtworkChoice.header(poster, backdrop, desktopHeader, room = true))
        // Where the poster does not stand beside the title, it is what the header falls back to if the backdrop cannot be read.
        assertEquals(listOf(backdrop, poster), ArtworkChoice.header(poster, backdrop, phoneHeader, room = false).fill)
    }

    @Test
    fun aPosterOnlyWorkIsPicturedByItsPoster() {
        assertEquals(listOf(poster), ArtworkChoice.portrait(poster, null))
        // A wide card crops it, as it does today; a phone's header is nearly poster-shaped and is filled by it.
        assertEquals(listOf(poster), ArtworkChoice.landscape(null, poster, 16f / 9f))
        assertEquals(ArtworkChoice.Header(listOf(poster), posterBeside = false), ArtworkChoice.header(poster, null, phoneHeader, room = false))
        // Where it stands beside the title it is not repeated behind it: the header is generated in its color.
        assertEquals(ArtworkChoice.Header(emptyList(), posterBeside = true), ArtworkChoice.header(poster, null, desktopHeader, room = true))
    }

    @Test
    fun aPosterIsNotStretchedAcrossAFrameTooWideForIt() {
        // Cropped to a band, a poster is its lettering: a wide hero or a phone on its side is generated instead.
        assertEquals(emptyList(), ArtworkChoice.landscape(null, poster, 2.2f))
        assertEquals(emptyList(), ArtworkChoice.header(poster, null, 915f / 340f, room = false).fill)
        assertTrue(ArtworkChoice.landscape(null, poster, ArtworkChoice.POSTER_FILLS_UP_TO).isNotEmpty())
    }

    @Test
    fun aBackdropOnlyWorkIsPicturedByItsBackdrop() {
        // Its poster card is the backdrop's middle, not a generated card.
        assertEquals(listOf(backdrop), ArtworkChoice.portrait(null, backdrop))
        assertEquals(listOf(backdrop), ArtworkChoice.landscape(backdrop, null, 3f))
        // And no generated card stands beside its title over the real backdrop.
        assertEquals(ArtworkChoice.Header(listOf(backdrop), posterBeside = false), ArtworkChoice.header(null, backdrop, desktopHeader, room = true))
        assertEquals(ArtworkChoice.Header(listOf(backdrop), posterBeside = false), ArtworkChoice.header(null, backdrop, phoneHeader, room = false))
    }

    @Test
    fun theTitleIsGeneratedOnlyWhenThereIsNoRealArtwork() {
        assertEquals(emptyList(), ArtworkChoice.portrait(null, null))
        assertEquals(emptyList(), ArtworkChoice.landscape(null, null, 1f))
        // With room, a generated card names the work beside its title; without, the header alone is generated.
        assertEquals(ArtworkChoice.Header(emptyList(), posterBeside = true), ArtworkChoice.header(null, null, desktopHeader, room = true))
        assertEquals(ArtworkChoice.Header(emptyList(), posterBeside = false), ArtworkChoice.header(null, null, phoneHeader, room = false))
    }

    @Test
    fun artworkAPlatformCannotReachIsSimplyNotThere() {
        // A television reading only videos never sees the poster.jpg beside them: the work has no local locator,
        // and is pictured by what it does have (here a provider's backdrop), by the same rule as everywhere.
        val local = ArtworkLocator.SourceFile(MediaLocation(SourceId("tv-folder"), "Movies/Heat/poster.jpg"))
        val seen = null
        assertEquals(listOf(backdrop), ArtworkChoice.portrait(seen, backdrop))
        assertEquals(listOf(local, backdrop), ArtworkChoice.portrait(local, backdrop), "where it can be read, it comes first")
        assertFalse(ArtworkChoice.header(seen, backdrop, desktopHeader, room = true).posterBeside)
    }
}
