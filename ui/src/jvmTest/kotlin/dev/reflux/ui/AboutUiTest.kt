package dev.reflux.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key as ComposeKey
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import dev.reflux.core.BuildIdentity
import dev.reflux.core.RefluxBuild
import dev.reflux.core.input.Key
import dev.reflux.metadata.tmdb.TmdbProvider
import dev.reflux.ui.legal.NoticeTarget
import dev.reflux.ui.legal.Notices
import dev.reflux.ui.state.AppState
import dev.reflux.ui.state.Destination
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The About page and the legal pages behind it: which build this is, who is credited and how, what Reflux
 * sends where, and every third-party license, reachable with a pointer, a keyboard, and a remote, and back.
 */
@OptIn(ExperimentalTestApi::class)
class AboutUiTest {
    private fun DesktopComposeUiTest.shows(text: String, substring: Boolean = false) =
        onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()

    private fun DesktopComposeUiTest.focusedText(): String? = runCatching {
        onNode(isFocused()).fetchSemanticsNode().let { node ->
            (node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
                node.children.flatMap { child -> child.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }).joinToString(" ")
        }
    }.getOrNull()

    private fun DesktopComposeUiTest.awaitFocusOn(text: String) =
        await("focus on \"$text\"", { focusedText() }) { onAllNodes(isFocused() and hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    private fun DesktopComposeUiTest.openAbout(app: AppState) {
        runOnIdle { app.navigator.open(Destination.About) }
        awaitText("Privacy")
    }

    @Test
    fun aboutIsReachedFromTheMediaSourcesAndSaysWhichBuildThisIs() {
        for ((width, height) in listOf(1440 to 900, 412 to 915)) screen(populated(), width = width, height = height) { app ->
            awaitText("Recently Added")
            runOnIdle { app.toggleSources() }
            // The version alone on the way there; the build on the page.
            awaitText("Version ${RefluxBuild.VERSION}, privacy, licenses, and credits")
            onNodeWithText("About Reflux").performClick()
            awaitText("Version ${RefluxBuild.VERSION} (8bd4bba)")
            assertEquals(Destination.About, app.navigator.current)
            // No section of the library is lit while Reflux itself is open.
            assertNull(app.navigator.highlighted)
            save("about-${width}x$height")
            runOnIdle { app.navigator.back() }
            awaitText("About Reflux")
            assertEquals(Destination.Sources, app.navigator.current)
        }
    }

    @Test
    fun theVersionIsTheOneTheBuildDeclaresAndAModifiedTreeSaysSo() {
        screen(populated(), FakePlatform(build = BuildIdentity(commit = "abc1234", modified = true))) { app ->
            openAbout(app)
            awaitText("Version ${RefluxBuild.VERSION} (abc1234, modified)")
        }
        // Where the build could not tell its commit, the version stands alone.
        screen(populated(), FakePlatform(build = BuildIdentity())) { app ->
            openAbout(app)
            awaitText("Version ${RefluxBuild.VERSION}")
        }
    }

    @Test
    fun theMetadataProviderIsCreditedAsItsTermsAsk() = screen(populated()) { app ->
        openAbout(app)
        // Word for word, with its approved logo, on the About page.
        awaitText("This product uses the TMDB API but is not endorsed or certified by TMDB.")
        assertTrue(shows("Titles, artwork, and details come from TMDB (themoviedb.org)."))
        val logo = onNodeWithContentDescription("TMDB", useUnmergedTree = true).fetchSemanticsNode()
        assertEquals(Role.Image, logo.config.getOrNull(SemanticsProperties.Role))
        // Less prominent than Reflux's own name, and drawn in its own proportions.
        val name = onNode(hasText("Reflux") and isHeading(), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val mark = logo.boundsInRoot
        assertTrue(mark.height < name.height, "the logo ($mark) is smaller than the name ($name)")
        val published = TmdbProvider.ATTRIBUTION.logo!!
        assertEquals(published.width / published.height, mark.width / mark.height, 0.05f)
        assertTrue(shows("Reflux looks up their titles on TMDB", substring = true), "the privacy note says what TMDB is asked")
    }

    @Test
    fun withoutAProviderNothingIsCreditedOrClaimed() = screen(TestWorld(metadata = false)) { app ->
        openAbout(app)
        assertFalse(shows("TMDB", substring = true))
        assertEquals(0, onAllNodesWithContentDescription("TMDB", useUnmergedTree = true).fetchSemanticsNodes().size)
        assertTrue(shows("Reflux has no account", substring = true))
    }

    @Test
    fun theNoticesAreThoseOfThisBuild() {
        // Tall enough for the whole list: a lazy list composes only what is on screen.
        screen(populated(), FakePlatform(notices = NoticeTarget.DESKTOP), height = 2400) { app ->
            runOnIdle { app.navigator.open(Destination.Notices) }
            awaitText("Skia")
            assertTrue(shows("mpv (libmpv) and FFmpeg") && shows("Java Native Access (JNA)"))
            assertFalse(shows("AndroidX Media3") || shows("OkHttp and Okio"))
            save("notices-desktop")
        }
        screen(populated(), FakePlatform(notices = NoticeTarget.ANDROID), height = 2400) { app ->
            runOnIdle { app.navigator.open(Destination.Notices) }
            awaitText("AndroidX Media3")
            assertTrue(shows("OkHttp and Okio") && shows("Bouncy Castle"))
            assertFalse(shows("Skia") || shows("mpv (libmpv) and FFmpeg"))
        }
    }

    @Test
    fun aLicenseIsShownAsItIs() = screen(populated()) { app ->
        runOnIdle { app.navigator.open(Destination.Notice("Bouncy Castle")) }
        awaitText("MIT")
        await("its text") { shows("Copyright (c) 2000-2026 The Legion of the Bouncy Castle Inc.", substring = true) }
        save("notice-bouncy-castle")
        // What Reflux does not include is said, not dressed up with a text it did not ship.
        runOnIdle { app.navigator.open(Destination.Notice("mpv (libmpv) and FFmpeg")) }
        awaitText("Reflux does not include it, so its license comes with the copy on this device.")
        assertTrue(shows("Playback uses the libmpv on this computer", substring = true))
    }

    @Test
    fun refluxIsProprietaryAndItsOwnTermsStandApartFromTheThirdParties() = screen(populated(), height = 2400) { app ->
        val notice = assertNotNull(Notices.refluxLicense)
        // An ownership notice, not an open-source license: none of their grants is in it.
        for (grant in listOf("Permission is hereby granted", "Licensed under", "GNU", "Redistribution and use", "MIT License")) {
            assertFalse(grant in notice, "Reflux's notice grants no open-source license: \"$grant\"")
        }
        assertEquals("Copyright © 2026 RSX Develops. All rights reserved.", Notices.refluxCopyright)
        openAbout(app)
        // The copyright with the build, and Reflux's own terms a row of their own, before the software it is built with.
        assertTrue(shows("Copyright © 2026 RSX Develops. All rights reserved."))
        val own = onNode(hasText("Copyright and ownership") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).fetchSemanticsNode()
        val others = onNode(hasText("Third-party software") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).fetchSemanticsNode()
        assertTrue(own.boundsInRoot.bottom <= others.boundsInRoot.top, "Reflux's own row comes first")
        assertTrue(shows("Reflux is proprietary software, not open source"))
        onNodeWithText("Copyright and ownership").performClick()
        await("the notice") { shows("Reflux is proprietary software. It is not open source", substring = true) }
        assertTrue(shows("nothing in this notice changes or restricts them", substring = true), "third-party licenses stay their own")
        save("about-ownership")
        // The third-party list is the third parties': Reflux is not among them, and each keeps its own license.
        runOnIdle { app.navigator.open(Destination.Notices) }
        awaitText("Kotlin and kotlinx libraries")
        assertTrue(shows("which Reflux's own copyright does not change", substring = true))
        assertTrue(Notices.components(NoticeTarget.DESKTOP).none { it.name == "Reflux" || "RSX Develops" in it.license })
    }

    @Test
    fun aKeyboardGoesInAndComesBackToWhereItWas() = screen(populated()) { app ->
        openAbout(app)
        // The first arrow starts on the page's first stop: who and what this is.
        runOnIdle { app.onKey(Key.ARROW_DOWN) }
        awaitFocusOn("Version ${RefluxBuild.VERSION}")
        repeat(3) { runOnIdle { app.onKey(Key.ARROW_DOWN) } }
        awaitFocusOn("Copyright and ownership")
        runOnIdle { app.onKey(Key.ARROW_DOWN) }
        awaitFocusOn("Third-party software")
        onNode(isFocused()).performKeyInput { pressKey(ComposeKey.Enter) }
        await("the notices") { app.navigator.current == Destination.Notices }
        runOnIdle { app.onKey(Key.ARROW_DOWN) }
        awaitFocusOn("Kotlin and kotlinx libraries")
        runOnIdle { app.onKey(Key.ARROW_DOWN) }
        awaitFocusOn("JetBrains Java Annotations")
        onNode(isFocused()).performKeyInput { pressKey(ComposeKey.Enter) }
        await("its notice") { app.navigator.current == Destination.Notice("JetBrains Java Annotations") }
        runOnIdle { app.onKey(Key.ARROW_DOWN) }
        awaitFocusOn("Apache License")
        // Escape goes back a page at a time, each time to the control that led on.
        runOnIdle { app.onKey(Key.ESCAPE) }
        awaitFocusOn("JetBrains Java Annotations")
        runOnIdle { app.onKey(Key.ESCAPE) }
        awaitFocusOn("Third-party software")
    }

    @Test
    fun aRemoteReadsALicenseLongerThanTheScreenAndFindsItsWayBack() = television { app ->
        awaitText("Recently Added")
        runOnIdle { app.toggleSources() }
        awaitText("Media sources")
        // Down the sources page to Reflux itself.
        repeat(12) { if (focusedText()?.contains("About Reflux") != true) remote(app, Key.ARROW_DOWN) }
        awaitFocusOn("About Reflux")
        remote(app, Key.DPAD_CENTER)
        // A television arrives on the page's first stop, not on the navigation above it.
        awaitFocusOn("Version ${RefluxBuild.VERSION}")
        repeat(4) { remote(app, Key.ARROW_DOWN) }
        awaitFocusOn("Third-party software")
        remote(app, Key.DPAD_CENTER)
        awaitFocusOn("Kotlin and kotlinx libraries")
        remote(app, Key.DPAD_CENTER)
        awaitFocusOn("Apache License")
        // Paragraph by paragraph through the Apache License, every stop on screen and inside the safe area.
        val seen = mutableListOf<String>()
        repeat(40) {
            remote(app, Key.ARROW_DOWN)
            val focused = onNode(isFocused()).fetchSemanticsNode().boundsInRoot.onCanvas()
            assertTrue(focused.top >= 0f && focused.bottom <= TV_HEIGHT.toFloat(), "the focused paragraph is on screen: $focused")
            assertTrue(focused.left >= 0f && focused.right <= TV_WIDTH.toFloat(), "and inside the screen's width: $focused")
            seen += focusedText().orEmpty()
        }
        assertTrue(seen.distinct().size >= 30, "focus moved through the text: ${seen.distinct().size} paragraphs")
        assertTrue(seen.any { "Grant of Copyright License" in it }, "it reached the license's terms")
        save("tv-license")
        // Back, a page at a time, to the control each page was left by.
        remote(app, Key.BACK)
        awaitFocusOn("Kotlin and kotlinx libraries")
        remote(app, Key.BACK)
        awaitFocusOn("Third-party software")
        remote(app, Key.BACK)
        awaitFocusOn("About Reflux")
        assertEquals(Destination.Sources, app.navigator.current)
    }

    @Test
    fun theLegalPagesReadWellToAssistiveTechnology() = screen(populated()) { app ->
        openAbout(app)
        val headings = onAllNodes(isHeading(), useUnmergedTree = true).fetchSemanticsNodes().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map { text -> text.text } }
        assertTrue("Reflux" in headings && "Privacy" in headings, "headings: $headings")
        // A row that leads on is a button with its words as its name.
        val row = onNode(hasText("Third-party software") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).fetchSemanticsNode()
        assertNotNull(row)
        // While the page gives way to the next, only one of them is there to be read.
        mainClock.autoAdvance = false
        runOnUiThread { app.navigator.open(Destination.Notices) }
        mainClock.advanceTimeBy(64)
        assertFalse(shows("Privacy"), "About, leaving, is not read out")
        assertTrue(shows("Third-party software"), "the notices, arriving, are")
        mainClock.autoAdvance = true
        awaitText("Kotlin and kotlinx libraries")
    }

    @Test
    fun everyNoticeHasItsTexts() {
        for (target in NoticeTarget.entries) {
            val components = Notices.components(target)
            assertTrue(components.size > 10, "$target lists its components")
            for (component in components) for (file in component.texts) {
                assertNotNull(Notices.text(file), "${component.name}: $file")
            }
        }
        // Reflux's own notice is the repository's, word for word.
        assertEquals(File("../LICENSE").readText(), assertNotNull(Notices.refluxLicense))
    }

    @Test
    fun aLicenseReflowsWithoutChangingAWord() {
        val text = "Licensed under the Apache License, Version 2.0 (the \"License\");\nyou may not use this file except\r\nin compliance with the License.\n\n   \n  2. Grant   of Copyright License.\n"
        assertEquals(
            listOf("Licensed under the Apache License, Version 2.0 (the \"License\"); you may not use this file except in compliance with the License.", "2. Grant of Copyright License."),
            Notices.paragraphs(text),
        )
        val apache = assertNotNull(Notices.text("apache-2.0.txt"))
        val words = { s: String -> s.split(Regex("\\s+")).filter(String::isNotEmpty) }
        assertEquals(words(apache), Notices.paragraphs(apache).flatMap(words))
    }

    private fun Rect.onCanvas() = Rect(left / 2f, top / 2f, right / 2f, bottom / 2f)
}
