package dev.reflux.core.search

import dev.reflux.core.model.MediaKind
import dev.reflux.core.playback.DynamicRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SmartQueryParserTest {
    private val vocabulary = SearchVocabulary(
        genres = setOf("Science Fiction", "Comedy", "Drama", "Documentary", "Animation", "Thriller"),
        people = setOf("Christopher Nolan", "Ryan Gosling", "Denis Villeneuve", "Emma Stone", "Jonathan Nolan"),
    )

    private fun parse(query: String) = SmartQueryParser.parse(query, vocabulary, currentYear = 2026)

    @Test
    fun roadmapExamples() {
        assertEquals(
            SmartQuery(kinds = setOf(MediaKind.MOVIE), watched = false, minHeight = 2160),
            parse("4k movies I haven't watched"),
        )
        assertEquals(SmartQuery(kinds = setOf(MediaKind.MOVIE), genres = setOf("Science Fiction"), maxRuntimeMinutes = 120), parse("science fiction movies under 2 hours"))
        assertEquals(SmartQuery(genres = setOf("Science Fiction"), maxRuntimeMinutes = 120), parse("sci-fi under 2 hours"))
        assertEquals(SmartQuery(kinds = setOf(MediaKind.MOVIE), people = setOf("Ryan Gosling")), parse("movies with Ryan Gosling"))
    }

    @Test
    fun surnamesNeedContext() {
        // Two Nolans in the library: a surname alone is ambiguous.
        assertTrue(parse("Nolan movies").people.isEmpty())
        assertEquals(setOf("Denis Villeneuve"), parse("Villeneuve movies").people)
        assertEquals(setOf("Denis Villeneuve"), parse("films by villeneuve").people)
        assertEquals(setOf("Christopher Nolan"), parse("christopher nolan").people)
        assertFalse(parse("stone").structured, "a bare surname stays title text")
    }

    @Test
    fun plainTitlesStayTitles() {
        val query = parse("The Dark Knight")
        assertFalse(query.structured)
        assertEquals("the dark knight", query.text)
        assertEquals("2012", parse("2012").text)
        assertFalse(parse("1917").structured)
    }

    @Test
    fun yearsAndDecades() {
        assertEquals(1990..1999, parse("90s comedies").years)
        assertEquals(setOf("Comedy"), parse("90s comedies").genres)
        assertEquals(2010..2019, parse("movies from the 2010s").years)
        assertEquals(2014..2014, parse("movies from 2014").years)
        assertEquals(1888..1999, parse("films before 2000").years)
        assertEquals(2011..2027, parse("shows after 2010").years)
    }

    @Test
    fun runtimes() {
        assertEquals(90, parse("comedies under 90 minutes").maxRuntimeMinutes)
        assertEquals(60, parse("documentaries less than an hour").maxRuntimeMinutes)
        assertEquals(150, parse("movies over 2 and a half hours").minRuntimeMinutes)
        assertEquals(90, parse("movies under 1.5 hours").maxRuntimeMinutes)
    }

    @Test
    fun qualityStateAndKinds() {
        assertEquals(DynamicRange.DOLBY_VISION, parse("dolby vision movies").dynamicRange)
        assertTrue(parse("hdr shows").hdr)
        assertEquals(setOf(MediaKind.SHOW), parse("hdr shows").kinds)
        assertTrue(parse("unfinished shows").inProgress)
        assertEquals(true, parse("watched thrillers").watched)
        assertTrue(parse("my favorites").favorite)
        assertEquals(setOf(MediaKind.SHOW), parse("show me tv shows").kinds)
        assertEquals(setOf(MediaKind.MOVIE), parse("show me animated movies").kinds)
        assertEquals(setOf("Animation"), parse("show me animated movies").genres)
    }

    @Test
    fun leftoverWordsBecomeTitleText() {
        val query = parse("unwatched star wars movies")
        assertEquals(false, query.watched)
        assertEquals("star wars", query.text)
    }

    @Test
    fun matchingDocuments() {
        val doc = SmartDocument(
            kind = MediaKind.MOVIE, genres = setOf("Science Fiction"), people = setOf("Denis Villeneuve"), year = 2016,
            runtimeMinutes = 116, maxHeight = 2160, dynamicRanges = setOf(DynamicRange.HDR10), watched = false,
            inProgress = false, favorite = false,
        )
        assertTrue(parse("4k sci-fi movies I haven't watched under 2 hours").matches(doc))
        assertTrue(parse("hdr movies").matches(doc))
        assertFalse(parse("dolby vision movies").matches(doc))
        assertFalse(parse("comedies").matches(doc))
        assertFalse(parse("movies under 90 minutes").matches(doc))
        assertTrue(parse("villeneuve movies from the 2010s").matches(doc))
        assertTrue(parse("4k movies").matches(doc.copy(maxHeight = 2076)), "slightly cropped frames still count")
        assertFalse(parse("4k movies").matches(doc.copy(maxHeight = 1080)))
    }
}
