package dev.reflux.playback.mpv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** B-058: a list option keeps the names this build of libmpv has, instead of being dropped for one it lacks. */
class ListOptionTest {
    @Test
    fun aNameTheBuildLacksIsLeftOutAndTheRestKeepTheirOrder() {
        val known = setOf("x11egl", "x11vk")
        assertEquals("x11egl,x11vk", MpvPlayer.knownEntries("x11egl,x11,x11vk") { it in known })
    }

    @Test
    fun nothingKnownLeavesTheEngineToChoose() {
        assertNull(MpvPlayer.knownEntries("x11,glx") { false })
    }

    @Test
    fun onlyListOptionsAreRetried() {
        assertTrue("gpu-context" in MpvPlayer.LIST_OPTIONS)
        assertTrue("http-header-fields" !in MpvPlayer.LIST_OPTIONS, "a header's value can hold commas")
    }
}
