package dev.reflux.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isFocused
import dev.reflux.core.model.Availability
import dev.reflux.core.source.MediaSource
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/** B-062: a television starts Home on the hero's main action, or on Details where the hero cannot play now. */
@OptIn(ExperimentalTestApi::class)
class HeroFocusTest {
    private fun androidx.compose.ui.test.DesktopComposeUiTest.focusedName(): String? =
        onAllNodes(isFocused()).fetchSemanticsNodes().firstOrNull()?.let { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull() ?: node.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text
        }

    @Test
    fun withTheLibraryAwayATelevisionStartsOnDetailsNotInTheNavigation() {
        val world = populated().also { world ->
            world.registry.register(object : MediaSource by world.source {
                override suspend fun availability() = Availability.UNAVAILABLE
            })
            runBlocking { world.library.refreshAvailability(world.registry.resolve(world.source.descriptor.id)!!) }
        }
        television(world) { _ ->
            awaitText("Recently Added")
            await("focus on the hero's Details", state = { focusedName() }) { focusedName() == "Details" }
        }
    }

    @Test
    fun withTheLibraryHereATelevisionStartsOnPlay() {
        television(populated()) { _ ->
            awaitText("Recently Added")
            await("focus on the hero's main action", state = { focusedName() }) { focusedName() == "Play" || focusedName() == "Resume" }
        }
    }
}
