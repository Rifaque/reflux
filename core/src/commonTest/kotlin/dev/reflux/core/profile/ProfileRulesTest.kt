package dev.reflux.core.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileRulesTest {
    @Test
    fun eachAccountOnAServerHasItsOwnStableDeviceIdentity() {
        val a = AccountDevices.id("inst", "jellyfin", "http://jf.local:8096", "Alex")
        val b = AccountDevices.id("inst", "jellyfin", "http://jf.local:8096", "Sam")
        assertNotEquals(a, b, "two accounts never share an identity")
        assertEquals(a, AccountDevices.id("inst", "jellyfin", "http://JF.local:8096/", "alex"), "the same account again is the same device")
        assertNotEquals(a, AccountDevices.id("other", "jellyfin", "http://jf.local:8096", "Alex"), "another installation is another device")
        assertNotEquals(a, AccountDevices.id("inst", "plex", "http://jf.local:8096", "Alex"), "and another kind of server")
        assertTrue(a.startsWith("inst-"))
        assertFalse("alex" in a.lowercase().removePrefix("inst-"), "no name in it")
    }

    @Test
    fun theDigestIsSha256() {
        // FIPS 180-4's own examples, and one longer than a block.
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Sha256.hex(""))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Sha256.hex("abc"))
        assertEquals("248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1", Sha256.hex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"))
        assertEquals("cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0", Sha256.hex("a".repeat(1_000_000)))
    }

    @Test
    fun aPinIsFourDigits() {
        assertTrue(PinPolicy.valid("0042"))
        listOf("", "123", "12345", "12a4", "١٢٣٤", " 123").forEach { assertFalse(PinPolicy.valid(it), it) }
    }

    @Test
    fun wrongEntriesSlowDownAndStayCapped() {
        (0 until PinPolicy.FREE_ATTEMPTS).forEach { assertNull(PinPolicy.lockAfter(it)) }
        assertEquals(30_000, PinPolicy.lockAfter(5))
        assertEquals(60_000, PinPolicy.lockAfter(6))
        assertEquals(480_000, PinPolicy.lockAfter(9))
        assertEquals(PinPolicy.MAX_LOCK_MS, PinPolicy.lockAfter(10))
        assertEquals(PinPolicy.MAX_LOCK_MS, PinPolicy.lockAfter(10_000))
    }

    @Test
    fun verifiersCompareExactly() {
        assertTrue(PinPolicy.same("abcd", "abcd"))
        assertFalse(PinPolicy.same("abcd", "abce"))
        assertFalse(PinPolicy.same("abcd", "abc"))
    }
}
