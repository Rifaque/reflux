package dev.reflux.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import dev.reflux.ui.state.AppState
import dev.reflux.ui.state.libraryState
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
    val sources by app.libraryState { sources() }
    val activity by app.activity.collectAsState()
    var removing by remember { mutableStateOf<SourceId?>(null) }
    Column(
        Modifier.width(400.dp).glass(MaterialRole.MODAL).padding(20.dp),
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
            Button("Add folder", { app.addFolder() }, icon = Icon.PLUS, style = ButtonStyle.PRIMARY)
            if (!sources.isNullOrEmpty()) Button("Refresh", { app.refresh() }, icon = Icon.REFRESH, style = ButtonStyle.QUIET, enabled = activity == PipelineActivity.Idle)
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
