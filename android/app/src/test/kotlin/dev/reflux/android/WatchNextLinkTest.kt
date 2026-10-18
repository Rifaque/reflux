package dev.reflux.android

import dev.reflux.android.launcher.WatchNextLink
import dev.reflux.core.model.MediaId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WatchNextLinkTest {
    @Test
    fun aLinkNamesTheWorkItWasMadeFor() {
        for (id in listOf("movie:heat-1995", "episode/show s01 e02", "ünïcode?&#=", "a".repeat(200))) {
            val link = WatchNextLink.of(MediaId(id))
            assertEquals(MediaId(id), WatchNextLink.itemId(link), link)
        }
    }

    @Test
    fun anythingElseNamesNothing() {
        for (link in listOf(null, "", "reflux://play/", "reflux://play/a/b", "reflux://play/a?b", "reflux://other/a", "https://example.com/play/a", "reflux://play/%20")) {
            assertNull(WatchNextLink.itemId(link), link.toString())
        }
    }
}
