package dev.reflux.core.net

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** One server at several addresses (B-003): which is tried, in what order, and how the home address is taken back. */
class EndpointSelectorTest {
    private var now = 1_000L
    private val lan = "http://192.168.1.20:8096"
    private val tailscale = "http://100.101.102.103:8096"
    private val public = "https://jellyfin.example.org"

    @Test
    fun theHomeNetworkIsPreferredThenTheOverlayThenTheRestAndOtherwiseTheOrderAdded() {
        assertEquals(0, EndpointSelector.rank(lan))
        assertEquals(0, EndpointSelector.rank("http://10.0.0.5:8096"))
        assertEquals(0, EndpointSelector.rank("http://172.20.1.1"))
        assertEquals(0, EndpointSelector.rank("http://nas.local:8096"))
        assertEquals(0, EndpointSelector.rank("http://jellyfin:8096"))
        assertEquals(0, EndpointSelector.rank("http://[fd12:3456::1]:8096"))
        assertEquals(1, EndpointSelector.rank(tailscale))
        assertEquals(1, EndpointSelector.rank("http://nas.tail1234.ts.net:8096"))
        assertEquals(1, EndpointSelector.rank("http://[fd7a:115c:a1e0::1]:8096"))
        assertEquals(2, EndpointSelector.rank(public))
        assertEquals(2, EndpointSelector.rank("http://8.8.8.8"))
        assertEquals(2, EndpointSelector.rank("http://172.32.0.1"), "outside 172.16/12")
        assertEquals(listOf(lan, tailscale, public), EndpointSelector.preferenceOrder(listOf(public, tailscale, lan)))
        assertEquals(listOf("http://a.example", "http://b.example"), EndpointSelector.preferenceOrder(listOf("http://a.example", "http://b.example")), "same kind: as added")
        assertEquals(listOf(lan), EndpointSelector.preferenceOrder(listOf(lan, "$lan/", lan.uppercase())), "one address, however written")
        assertFailsWith<IllegalArgumentException> { EndpointSelector(emptyList(), { now }) }
    }

    @Test
    fun requestsStayWithTheAddressThatLastAnswered() {
        val selector = EndpointSelector(listOf(tailscale, lan), { now })
        assertEquals(lan, selector.current, "the home address first, until one has answered")
        assertEquals(listOf(lan, tailscale), selector.requestOrder())
        // Away from home: the LAN address does not answer, Tailscale does.
        selector.failed(lan)
        selector.succeeded(tailscale)
        assertEquals(tailscale, selector.current)
        assertEquals(listOf(tailscale, lan), selector.requestOrder(), "no waiting for the LAN address on every request")
        val health = selector.health()
        assertEquals(listOf(lan, tailscale), health.map { it.address })
        assertEquals(listOf(false, true), health.map { it.inUse })
        assertEquals(now, health.first().lastFailureAt)
        assertEquals(now, health.last().lastSuccessAt)
    }

    @Test
    fun anAvailabilityCheckTakesTheHomeAddressBackAtMostOnceAMinute() {
        val selector = EndpointSelector(listOf(lan, tailscale), { now }, recheckAfterMs = 60_000)
        selector.failed(lan)
        selector.succeeded(tailscale)
        // The first check after falling back tries home first again; checks within the minute do not.
        assertEquals(listOf(lan, tailscale), selector.probeOrder())
        selector.failed(lan)
        selector.succeeded(tailscale)
        now += 30_000
        assertEquals(listOf(tailscale, lan), selector.probeOrder())
        now += 31_000
        assertEquals(listOf(lan, tailscale), selector.probeOrder(), "a minute on, home is tried again")
        // Home again: the LAN address answers and is the one used from then on.
        selector.succeeded(lan)
        assertEquals(lan, selector.current)
        assertEquals(listOf(lan, tailscale), selector.requestOrder())
        assertEquals(listOf(lan, tailscale), selector.probeOrder(), "on the most preferred, a check simply asks it")
    }

    @Test
    fun anAddressItDoesNotKnowChangesNothing() {
        val selector = EndpointSelector(listOf(lan), { now })
        selector.succeeded(public)
        selector.failed(public)
        assertEquals(lan, selector.current)
        assertTrue(selector.health().single().lastFailureAt == null)
    }
}
