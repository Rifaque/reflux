package dev.reflux.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onAllNodesWithTag
import dev.reflux.core.input.ControllerFamily
import dev.reflux.core.input.GamepadButton
import dev.reflux.core.input.InputModality
import dev.reflux.design.AdaptiveLayout
import dev.reflux.ui.components.CARD_CAPTION
import dev.reflux.ui.components.FocusRingReach
import dev.reflux.ui.components.STATE_MARK_INSET
import dev.reflux.ui.components.STATE_MARK_SIZE
import dev.reflux.ui.screens.SuggestionGap
import dev.reflux.ui.state.AppState
import dev.reflux.ui.state.Destination
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Cards and chips as they are drawn (B-009, B-015, B-032, B-010): a focused card's ring never crosses its title or the
 * section's title above, cards in a row line up, a card's state marks never meet, and suggestion chips keep their rings
 * apart and come whole into view.
 */
@OptIn(ExperimentalTestApi::class)
class CardLayoutTest {
    private fun DesktopComposeUiTest.press(app: AppState, button: GamepadButton) {
        runOnIdle { app.onGamepadButton(button, ControllerFamily.XBOX) }
        waitForIdle()
    }

    private fun DesktopComposeUiTest.focused(): Rect = onNode(isFocused()).fetchSemanticsNode().boundsInRoot

    private fun Rect.overlaps(other: Rect) = left < other.right && other.left < right && top < other.bottom && other.top < bottom

    @Test
    fun aFocusedCardsRingNeverCrossesItsTitleOrTheRowsTitle() {
        for ((width, height) in listOf(1440 to 900, 1024 to 768, 960 to 540)) {
            screen(populated(), width = width, height = height) { app ->
                awaitText("Recently Added")
                press(app, GamepadButton.DPAD_DOWN)
                repeat(3) { step ->
                    press(app, GamepadButton.DPAD_DOWN)
                    // Let the lift and the words' step settle.
                    mainClock.advanceTimeBy(600)
                    waitForIdle()
                    val card = focused()
                    if (card.height < 100f) return@repeat
                    val ring = card.inflate(FocusRingReach.value)
                    val captions = onAllNodesWithTag(CARD_CAPTION).fetchSemanticsNodes().map { it.boundsInRoot }
                        .filter { it.left < card.right && card.left < it.right && it.top >= card.top }
                    assertTrue(captions.isNotEmpty(), "the focused card has its words beneath it")
                    val own = captions.minBy { it.top }
                    assertTrue(!ring.overlaps(own), "$width×$height step $step: the ring $ring crosses the title $own")
                    val headings = onAllNodes(SemanticsMatcher("heading") { it.config.contains(SemanticsProperties.Heading) }).fetchSemanticsNodes()
                        .map { it.boundsInRoot }.filter { it.bottom <= card.top + 1f && it.left < card.right + 400f }
                    headings.maxByOrNull { it.bottom }?.let { above ->
                        assertTrue(!ring.overlaps(above), "$width×$height step $step: the ring $ring reaches the row's title $above")
                    }
                }
            }
        }
    }

    @Test
    fun cardsInARowShareTheirTopAndBottom() {
        // B-015: with and without artwork (a generated card), and with a controller's larger cards.
        for (posters in listOf(true, false)) {
            screen(populated(), FakePlatform(posters = posters)) { app ->
                awaitText("Recently Added")
                for (directional in listOf(false, true)) {
                    if (directional) press(app, GamepadButton.DPAD_UP)
                    mainClock.advanceTimeBy(800)
                    waitForIdle()
                    val cards = onAllNodes(SemanticsMatcher("a card") { it.config.getOrNull(SemanticsProperties.Role) == Role.Button && it.boundsInRoot.height > 150f })
                        .fetchSemanticsNodes().map { it.boundsInRoot }
                        .filter { !it.overlaps(focusedOrEmpty()) }
                    val rows = cards.groupBy { (it.top / 20f).toInt() }.values.filter { it.size > 1 }
                    assertTrue(rows.isNotEmpty(), "rows of cards were found")
                    for (row in rows) {
                        val tops = row.map { it.top }
                        val bottoms = row.map { it.bottom }
                        assertTrue(tops.max() - tops.min() < 1f && bottoms.max() - bottoms.min() < 1f, "posters=$posters directional=$directional: $row")
                    }
                }
            }
        }
    }

    private fun DesktopComposeUiTest.focusedOrEmpty(): Rect = runCatching { focused() }.getOrDefault(Rect.Zero)

    @Test
    fun aCardsStateMarksNeverMeetOnTheNarrowestCard() {
        // B-032: not connected sits at the top start, watched at the top end, each a fixed small mark.
        val narrowest = listOf(
            AdaptiveLayout.profile(360f, 800f, false, InputModality.TOUCH),
            AdaptiveLayout.profile(700f, 1000f, false, InputModality.TOUCH),
            AdaptiveLayout.profile(960f, 540f, true, InputModality.DIRECTIONAL),
            AdaptiveLayout.profile(1440f, 900f, false, InputModality.POINTER),
        ).minOf { it.posterWidthDp }
        val needed = 2 * (STATE_MARK_SIZE.value + STATE_MARK_INSET.value)
        assertTrue(needed + 8f <= narrowest, "two marks need $needed dp; the narrowest card is $narrowest dp")
    }

    @Test
    fun suggestionChipsKeepTheirRingsApartAndScrollWholeIntoView() {
        // B-010: on a desktop window and on a phone.
        for ((width, height) in listOf(1440 to 900, 412 to 900)) {
            screen(populated(), width = width, height = height) { app ->
                awaitText("Recently Added")
                runOnIdle { app.go(Destination.Search) }
                await("the field") { app.textInputActive }
                assertTrue(SuggestionGap > FocusRingReach * 1.5f, "a ring reaches ${FocusRingReach}, the gap is $SuggestionGap")
                press(app, GamepadButton.DPAD_DOWN)
                var last: Rect? = null
                repeat(8) {
                    mainClock.advanceTimeBy(400)
                    waitForIdle()
                    val chip = focused()
                    assertTrue(chip.left >= 0f && chip.right <= width.toFloat(), "$width: the focused chip is whole on screen ($chip)")
                    val row = onAllNodes(hasTestTag("suggestion")).fetchSemanticsNodes().map { it.boundsInRoot }.sortedBy { it.left }
                    // Tiles in rows (B-057): neighbours in a row are apart by the gap, and so are the rows.
                    // A focused tile lifts a little; it is told by its centre, which the lift does not move.
                    val lines = row.groupBy { (it.center.y / 8f).toInt() }.toSortedMap().values.toList()
                    lines.forEach { line -> line.sortedBy { it.left }.zipWithNext().forEach { (a, b) -> assertTrue(b.left - a.right >= SuggestionGap.value - 4f, "$width: tiles $a and $b are apart") } }
                    lines.zipWithNext().forEach { (a, b) -> assertTrue(b.minOf { it.top } - a.maxOf { it.bottom } >= SuggestionGap.value - 4f, "$width: rows are apart") }
                    last = chip
                    press(app, GamepadButton.DPAD_RIGHT)
                }
                val chips = onAllNodes(hasTestTag("suggestion")).fetchSemanticsNodes().map { it.boundsInRoot }
                assertTrue(chips.size >= 3, "suggestions shown")
            }
        }
    }
}
