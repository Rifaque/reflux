package dev.reflux.core.identify

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** B-024: a server's name for a version is an edition only when it names one; its quality words are not one. */
class VersionNamesTest {
    @Test
    fun qualityWordsAndTheTitleAreNotAnEdition() {
        for (name in listOf(
            "2160p HDR", "Heat 4K", "Heat 1080p", "1080p", "4K HDR10+", "2160p DV HDR10", "Dolby Vision", "UHD BluRay REMUX HEVC",
            "WEB-DL 1080p x264 DDP5.1", "Heat (1995) - 2160p", "Heat.1995.2160p.UHD.BluRay.x265.10bit.HDR.TrueHD.7.1.Atmos",
            "720p HLG", "SDR 1080p AVC", " ", "Heat",
        )) {
            assertNull(VersionNames.edition(name, "Heat"), "\"$name\" says nothing beyond its quality and title")
        }
        assertNull(VersionNames.edition(null, "Heat"))
    }

    @Test
    fun aRealEditionIsKeptByItsProperName() {
        assertEquals("Director's Cut", VersionNames.edition("Directors Cut", "Blade Runner"))
        assertEquals("Director's Cut", VersionNames.edition("Blade Runner - 2160p Director's Cut", "Blade Runner"))
        assertEquals("Extended", VersionNames.edition("1080p - Extended", "The Two Towers"))
        assertEquals("IMAX", VersionNames.edition("IMAX 2160p HDR", "Dune"))
        assertEquals("Final Cut", VersionNames.edition("Blade.Runner.Final.Cut.1080p", "Blade Runner"))
    }

    @Test
    fun whatANameSaysBeyondItsQualityIsKept() {
        assertEquals("Fan Edit", VersionNames.edition("Heat 1080p Fan Edit", "Heat"))
        assertEquals("Commentary", VersionNames.edition("2160p HDR - Commentary", "Heat"))
        // Without a title to take out, the rest of a name is what it says.
        assertEquals("Heat", VersionNames.edition("Heat 4K", null))
    }
}
