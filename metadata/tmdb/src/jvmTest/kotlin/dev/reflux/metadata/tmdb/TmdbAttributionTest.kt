package dev.reflux.metadata.tmdb

import dev.reflux.core.net.HttpFetcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * TMDB's terms ask for its notice word for word and for one of its approved logos, unmodified. The logo is
 * compared with the file TMDB publishes (`tmdb-primary-short-blue.svg`, saved from
 * themoviedb.org/about/logos-attribution on 2026-10-19), so it cannot drift from it.
 */
class TmdbAttributionTest {
    private val published = assertNotNull(javaClass.getResource("/tmdb-primary-short-blue.svg")).readText()

    @Test
    fun theNoticeIsTheOneTheTermsRequire() {
        val attribution = TmdbProvider("token", HttpFetcher { error("no network in this test") }).attribution
        assertEquals("This product uses the TMDB API but is not endorsed or certified by TMDB.", attribution.notice)
        assertEquals("TMDB", attribution.name)
        assertEquals("themoviedb.org", attribution.site)
    }

    @Test
    fun theLogoIsThePublishedOneAsItIs() {
        val logo = assertNotNull(TmdbProvider.ATTRIBUTION.logo)
        assertEquals(Regex(""" d="([^"]+)"""").find(published)!!.groupValues[1], logo.pathData)
        assertEquals("0 0 ${logo.width} ${logo.height}", Regex("""viewBox="([^"]+)"""").find(published)!!.groupValues[1])
        val stops = Regex("""<stop offset="([^"]+)" stop-color="#([0-9a-f]{6})"""").findAll(published)
            .map { it.groupValues[1].toFloat() to (0xFF000000 or it.groupValues[2].toLong(16)) }.toList()
        assertEquals(stops, logo.stops)
        // Left to right across the whole logo, as the file draws it.
        assertEquals("y1=\"17.76\" x2=\"${logo.width}\" y2=\"17.76\"", Regex("""<linearGradient id="linear-gradient" ([^>]+) gradientUnits""").find(published)!!.groupValues[1])
    }
}
