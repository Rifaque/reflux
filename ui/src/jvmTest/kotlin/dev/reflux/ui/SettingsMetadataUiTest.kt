package dev.reflux.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import dev.reflux.library.onlineMetadata
import dev.reflux.ui.screens.ONLINE_METADATA_UNAVAILABLE
import dev.reflux.ui.state.Destination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Settings says when a build has no metadata provider (a development build made without one, as on a Xiaomi Pad 7 on
 * 2026-11-16, left placeholders unexplained), and says nothing of the kind when it has one. It is a sentence, never a
 * switch that does nothing, and it is not the switch turned off.
 */
@OptIn(ExperimentalTestApi::class)
class SettingsMetadataUiTest {
    private fun DesktopComposeUiTest.openSettings(app: dev.reflux.ui.state.AppState) {
        runOnIdle { app.navigator.show(Destination.Settings) }
        runCatching { onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Media Atmosphere")) }
        awaitText("Media Atmosphere")
    }

    private fun DesktopComposeUiTest.saysUnavailable() = onAllNodesWithText(ONLINE_METADATA_UNAVAILABLE).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun theWordingIsTheApprovedOne() = assertEquals("Online metadata is unavailable in this build.", ONLINE_METADATA_UNAVAILABLE)

    @Test
    fun withoutAProviderSettingsSaysSoInsteadOfASwitch() {
        // A desktop window, then a phone held upright.
        for ((width, height) in listOf(1440 to 900, 412 to 915)) screen(populated(metadata = false), width = width, height = height) { app ->
            openSettings(app)
            runCatching { onNode(hasScrollToNodeAction()).performScrollToNode(hasText(ONLINE_METADATA_UNAVAILABLE)) }
            awaitText(ONLINE_METADATA_UNAVAILABLE)
            val node = onNodeWithText(ONLINE_METADATA_UNAVAILABLE).fetchSemanticsNode()
            assertNull(node.config.getOrNull(SemanticsProperties.ToggleableState), "nothing to switch")
            assertTrue(node.config.getOrNull(SemanticsProperties.Role) != Role.Switch)
            assertTrue(onAllNodesWithText("Online metadata & artwork").fetchSemanticsNodes().isEmpty(), "no switch for lookups that cannot happen")
            assertTrue(!app.hasMetadataProvider)
        }
    }

    @Test
    fun onATelevisionItIsThereToReadToo() = television(populated(metadata = false)) { app ->
        openSettings(app)
        runCatching { onNode(hasScrollToNodeAction()).performScrollToNode(hasText(ONLINE_METADATA_UNAVAILABLE)) }
        awaitText(ONLINE_METADATA_UNAVAILABLE)
        assertTrue(onNodeWithText(ONLINE_METADATA_UNAVAILABLE).fetchSemanticsNode().config.contains(SemanticsProperties.Focused), "a remote can reach it")
    }

    @Test
    fun withAProviderTheSwitchIsThereAndNothingIsSaidAboutAnUnavailableOne() = screen(populated()) { app ->
        openSettings(app)
        runCatching { onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Online metadata & artwork")) }
        awaitText("Online metadata & artwork")
        assertTrue(!saysUnavailable())
        // Turned off, lookups are off; that is not "unavailable", and the switch stays where it was.
        onNodeWithText("Online metadata & artwork").performClick()
        await("lookups to be off") { !app.library.onlineMetadata }
        assertTrue(!saysUnavailable(), "off is not unavailable")
        assertTrue(onAllNodesWithText("Online metadata & artwork").fetchSemanticsNodes().isNotEmpty())
        onNodeWithText("Online metadata & artwork").performClick()
        await("lookups to be on") { app.library.onlineMetadata }
    }
}
