package dev.reflux.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import dev.reflux.design.MaterialRole
import dev.reflux.ui.theme.GlassScene
import dev.reflux.ui.theme.LocalBackdropBlur
import dev.reflux.ui.theme.glass
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class GlassTest {
    /** The middle of a sheet floating over a page of one [page] color. */
    private fun sheetOver(page: Color, blurs: Boolean): Color {
        var seen = Color.Unspecified
        runDesktopComposeUiTest(400, 300) {
            setContent {
                CompositionLocalProvider(LocalBackdropBlur provides blurs) {
                    GlassScene(
                        Modifier.fillMaxSize(),
                        overlay = { Box(Modifier.fillMaxSize(), Alignment.Center) { Box(Modifier.size(200.dp, 150.dp).glass(MaterialRole.MODAL)) } },
                    ) { Box(Modifier.fillMaxSize().background(page)) }
                }
            }
            waitForIdle()
            val pixels = onRoot().captureToImage().toPixelMap()
            seen = pixels[200, 150]
        }
        return seen
    }

    private fun distance(a: Color, b: Color) = abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)

    @Test
    fun withoutBlurAPageDoesNotShowThroughASheet() {
        // Android before 12 (every Fire OS television) cannot blur: the sharp page showed through the tint.
        val overWhite = sheetOver(Color.White, blurs = false)
        val overBlack = sheetOver(Color.Black, blurs = false)
        assertTrue(distance(overWhite, overBlack) < 0.02f, "the sheet over white $overWhite and over black $overBlack should look the same")
    }

    @Test
    fun withBlurASheetStillTakesOnThePageBeneath() {
        // The frosted look is unchanged where blur exists: the page's light comes through, softened.
        assertTrue(distance(sheetOver(Color.White, blurs = true), sheetOver(Color.Black, blurs = true)) > 0.1f)
    }
}
