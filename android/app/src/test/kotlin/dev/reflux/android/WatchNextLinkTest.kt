package dev.reflux.android

import dev.reflux.android.launcher.WatchNextLink
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.ProfileId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WatchNextLinkTest {
    @Test
    fun aLinkNamesTheWorkAndTheProfileItWasMadeFor() {
        for (id in listOf("movie:heat-1995", "episode/show s01 e02", "ünïcode?&#=", "a".repeat(200))) {
            for (profile in listOf(ProfileId.DEFAULT, ProfileId("p/1?x"))) {
                val link = WatchNextLink.of(MediaId(id), profile)
                assertEquals(WatchNextLink.Target(MediaId(id), profile), WatchNextLink.target(link), link)
                assertEquals(MediaId(id), WatchNextLink.itemId(link), link)
            }
        }
    }

    @Test
    fun aLinkFromBeforeProfilesStillNamesItsWork() {
        assertEquals(WatchNextLink.Target(MediaId("movie:heat"), null), WatchNextLink.target("reflux://play/movie%3Aheat"))
        assertEquals(WatchNextLink.Target(MediaId("m"), null), WatchNextLink.target(WatchNextLink.of(MediaId("m"))))
    }

    @Test
    fun anythingElseNamesNothing() {
        for (
            link in listOf(
                null, "", "reflux://play/", "reflux://play/a/b", "reflux://play/a?b", "reflux://other/a", "https://example.com/play/a", "reflux://play/%20",
                "reflux://watch/", "reflux://watch/p", "reflux://watch/p/", "reflux://watch//a", "reflux://watch/p/a/b", "reflux://watch/p/a?b",
            )
        ) {
            assertNull(WatchNextLink.target(link), link.toString())
        }
    }

    @Test
    fun aCardOneViewerRemovedIsNotRemovedForAnother() {
        val alex = ProfileId("default")
        val sam = ProfileId("p1")
        val stored = mapOf(
            dev.reflux.android.launcher.WatchNextDismissals.prefix(alex) + "heat" to 10L,
            dev.reflux.android.launcher.WatchNextDismissals.prefix(sam) + "alien" to 20L,
            "heat" to 5L, // from before profiles: nobody's
        )
        assertEquals(mapOf("heat" to 10L), dev.reflux.android.launcher.WatchNextDismissals.of(alex, stored))
        assertEquals(mapOf("alien" to 20L), dev.reflux.android.launcher.WatchNextDismissals.of(sam, stored))
        assertEquals(false, dev.reflux.android.launcher.WatchNextDismissals.scoped("heat"))
    }
}

