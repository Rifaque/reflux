package dev.reflux.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.reflux.core.playback.PlayerStatus
import dev.reflux.ui.RefluxTheme
import dev.reflux.ui.components.Icon
import dev.reflux.ui.components.RefluxLoader
import dev.reflux.ui.state.AppState
import dev.reflux.ui.state.PlaybackSession
import dev.reflux.ui.theme.LocalTypography
import dev.reflux.ui.theme.RefluxColors
import dev.reflux.ui.theme.Text

/**
 * What Reflux draws in the system's own window (Android's picture-in-picture), over the picture: nothing, but for what the
 * viewer must not miss. The system draws the window's controls, so none are here (docs/planning/M10-PLAYER-SPEC.md, 6.4).
 *
 * - A wait that goes on past the grace: the Reflux loader.
 * - A failure, or "Still watching?": said in words over a dimmed picture; expanding the window shows the player with it.
 */
@Composable
fun SystemWindowOverlay(app: AppState, session: PlaybackSession, modifier: Modifier = Modifier) {
    RefluxTheme(app, modifier) {
        val state by session.state.collectAsState()
        val said = when {
            session.error != null -> "Playback stopped"
            session.upNext?.asks == true -> "Still watching?"
            else -> null
        }
        val waiting = said == null && (state.status == PlayerStatus.LOADING || state.status == PlayerStatus.BUFFERING)
        Box(
            Modifier.fillMaxSize().then(if (said != null) Modifier.background(Color.Black.copy(alpha = 0.72f)) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            when {
                said != null -> Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icon.INFO, size = 20.dp, tint = RefluxColors.warning)
                    Text(said, style = LocalTypography.current.captionStrong, maxLines = 2)
                }
                waiting -> RefluxLoader(size = 28.dp, delayMs = ChromeClock.LOADING_GRACE_MS)
            }
        }
    }
}
