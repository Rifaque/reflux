package dev.reflux.ui.screens

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import dev.reflux.core.model.Availability
import dev.reflux.core.model.SourceId
import dev.reflux.core.source.SourceLocality
import dev.reflux.design.MaterialRole
import dev.reflux.library.PipelineActivity
import dev.reflux.library.SourceRecord
import dev.reflux.ui.components.Button
import dev.reflux.ui.components.ButtonStyle
import dev.reflux.ui.components.Icon
import dev.reflux.ui.components.IconButton
import dev.reflux.ui.components.absorbsPointer
import dev.reflux.ui.components.focusPanel
import dev.reflux.ui.state.AppState
import dev.reflux.library.downloads
import dev.reflux.library.userSources
import dev.reflux.ui.state.libraryState
import dev.reflux.ui.theme.LocalLayout
import dev.reflux.ui.theme.LocalTypography
import dev.reflux.ui.theme.RefluxColors
import dev.reflux.ui.theme.Text
import dev.reflux.ui.theme.glass

/**
 * Where the library comes from. Deliberately small: add a folder, see whether each source is reachable,
 * remove one. Everything else about sources is automatic.
 */
@Composable
internal fun SourcesPanel(app: AppState) {
    val sources by app.libraryState { userSources() }
    val activity by app.activity.collectAsState()
    val metadataUnreachable by app.metadataUnreachable.collectAsState()
    var removing by remember { mutableStateOf<SourceId?>(null) }
    val first = remember { FocusRequester() }
    val showsFocus = LocalLayout.current.showsFocus
    // Opened without a pointer, the panel takes focus; focus then stays inside it until it is closed.
    LaunchedEffect(Unit) { if (showsFocus) runCatching { first.requestFocus() } }
    Column(
        Modifier.widthIn(max = 400.dp).fillMaxWidth().glass(MaterialRole.MODAL).absorbsPointer()
            .focusPanel().focusProperties { onExit = { cancelFocusChange() } }.focusGroup()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Media sources", style = LocalTypography.current.title)
            Text(
                "Reflux only reads from these. Your files are never renamed, moved, or changed.",
                style = LocalTypography.current.caption,
                color = RefluxColors.textSecondary,
            )
        }
        sources.orEmpty().forEach { source ->
            if (removing == source.id) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Remove ${source.displayName}?", style = LocalTypography.current.bodyStrong)
                    Text(
                        "Its movies and shows leave your library. What you have watched is remembered, and the files stay where they are.",
                        style = LocalTypography.current.caption,
                        color = RefluxColors.textSecondary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button("Remove", {
                            removing = null
                            app.removeSource(source.id)
                        })
                        Button("Keep", { removing = null }, style = ButtonStyle.QUIET)
                    }
                }
            } else {
                SourceRow(source, activity) { removing = source.id }
            }
        }
        Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button("Add folder", { app.addFolder() }, Modifier.focusRequester(first), icon = Icon.PLUS, style = ButtonStyle.PRIMARY)
            if (!sources.isNullOrEmpty()) Button("Refresh", { app.refresh() }, icon = Icon.REFRESH, style = ButtonStyle.QUIET, enabled = activity == PipelineActivity.Idle)
        }
        // Where the library comes from is also where the question of doing without it belongs.
        val keeps by app.libraryState { app.platform.offline?.let { it.possible() || downloads().isNotEmpty() } ?: false }
        if (keeps == true) {
            Button("Watch offline", {
                app.sourcesOpen = false
                app.offlineOpen = true
            }, icon = Icon.DOWNLOAD, style = ButtonStyle.QUIET)
        }
        if (metadataUnreachable) {
            Text(
                "Titles and artwork couldn't be fetched just now. Your library works without them, and Reflux will try again.",
                style = LocalTypography.current.caption,
                color = RefluxColors.textTertiary,
            )
        }
    }
}

@Composable
private fun SourceRow(source: SourceRecord, activity: PipelineActivity, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        val offline = source.availability == Availability.UNAVAILABLE
        Icon(if (offline) Icon.OFFLINE else Icon.FOLDER, tint = if (offline) RefluxColors.unavailable else RefluxColors.textSecondary)
        Column(Modifier.weight(1f)) {
            Text(source.displayName, style = LocalTypography.current.bodyStrong, maxLines = 1)
            val scanning = (activity as? PipelineActivity.Scanning)?.sourceName == source.displayName
            Text(
                when {
                    scanning -> "Scanning…"
                    offline -> "Not connected. Your library stays browsable."
                    else -> where(source.locality)
                },
                style = LocalTypography.current.caption,
                color = RefluxColors.textTertiary,
                maxLines = 1,
            )
        }
        IconButton(Icon.CLOSE, "Remove ${source.displayName}", onRemove, size = 32.dp, tint = RefluxColors.textSecondary)
    }
}

private fun where(locality: SourceLocality): String = when (locality) {
    SourceLocality.DEVICE -> "On this device"
    SourceLocality.LOCAL_NETWORK -> "On your network"
    SourceLocality.REMOTE -> "Remote"
}
