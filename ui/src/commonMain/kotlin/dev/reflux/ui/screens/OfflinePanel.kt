package dev.reflux.ui.screens

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import dev.reflux.core.offline.DownloadState
import dev.reflux.design.MaterialRole
import dev.reflux.ui.components.Button
import dev.reflux.ui.components.ButtonStyle
import dev.reflux.ui.components.Icon
import dev.reflux.ui.components.IconButton
import dev.reflux.ui.components.ProgressBar
import dev.reflux.ui.components.absorbsPointer
import dev.reflux.ui.components.focusPanel
import dev.reflux.ui.components.pane
import dev.reflux.ui.state.AppState
import dev.reflux.ui.state.OfflineLine
import dev.reflux.ui.state.Problems
import dev.reflux.ui.state.offlineModel
import dev.reflux.ui.state.offlineState
import dev.reflux.ui.theme.LocalLayout
import dev.reflux.ui.theme.LocalTypography
import dev.reflux.ui.theme.RefluxColors
import dev.reflux.ui.theme.Text
import dev.reflux.ui.theme.glass

/**
 * What Reflux would keep of a work (a film, an episode, a season, a show) and the copies of it there are, or,
 * opened without a work, what it would keep of what is being watched. Deliberately not a download manager:
 * one proposal with what it costs, and the copies with the one thing to do about each; every copy is on the
 * Downloads page. Which version, which codec, which source is Reflux's to work out and is not asked here.
 */
@Composable
internal fun OfflinePanel(app: AppState) {
    val offline = app.platform.offline ?: return
    // While the sheet closes it keeps showing what it showed, rather than turning into the general sheet on its way out.
    var shown by remember { mutableStateOf(app.offlineReview) }
    if (app.offlineOpen) shown = app.offlineReview
    val reviewing = shown
    val loaded by app.offlineState(reviewing) { offlineModel(offline, reviewing) }
    val model = loaded
    var removing by remember { mutableStateOf<String?>(null) }
    var removingAll by remember(reviewing) { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    val showsFocus = LocalLayout.current.showsFocus
    // The panel takes focus once it has something to give it to, and keeps it, or Tab would walk the page behind
    // it however the panel was opened. When a copy it lists goes, focus goes back to its main button rather than nowhere.
    val copies = model?.downloads?.map { it.id }
    LaunchedEffect(model != null, copies?.size) {
        if (model == null || !app.offlineOpen) return@LaunchedEffect
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    // Asked whether to remove, focus is on keeping: a press too many keeps the copy.
    val answer = remember { FocusRequester() }
    LaunchedEffect(removing, removingAll) {
        if (!showsFocus || (removing == null && !removingAll)) return@LaunchedEffect
        withFrameNanos { }
        runCatching { answer.requestFocus() }
    }
    Column(
        Modifier.widthIn(max = 420.dp).fillMaxWidth().glass(MaterialRole.MODAL).absorbsPointer()
            .pane("Watch offline") { app.offlineOpen = false }
            .focusPanel().focusProperties { onExit = { cancelFocusChange() } }.focusGroup()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Watch offline", style = LocalTypography.current.title)
            model?.subject?.let { Text(it, style = LocalTypography.current.bodyStrong, maxLines = 1) }
            Text(
                if (reviewing != null) Problems.OFFLINE_PREPARING else "Reflux keeps a copy on this device, in the version that plays best here. Where you are in it carries over, both ways.",
                style = LocalTypography.current.caption,
                color = RefluxColors.textSecondary,
            )
        }
        if (model == null) return@Column
        Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (model.proposal != null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (reviewing == null) Text("Ready to keep", style = LocalTypography.current.captionStrong, color = RefluxColors.textSecondary)
                    model.proposed.forEach { Line(it) }
                    model.room?.let { Text(it, style = LocalTypography.current.caption, color = RefluxColors.textSecondary) }
                }
            }
            model.leftOut.forEach { Text(it, style = LocalTypography.current.caption, color = if (reviewing != null) RefluxColors.textSecondary else RefluxColors.textTertiary) }
            if (model.downloads.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Downloads", style = LocalTypography.current.captionStrong, color = RefluxColors.textSecondary)
                    model.downloads.forEach { line ->
                        if (removing == line.id) {
                            Column(Modifier.pane("Remove ${line.title} from this device?"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Remove ${line.title} from this device?", style = LocalTypography.current.bodyStrong)
                                Text("Only the copy goes. It stays in your library, with what you have watched.", style = LocalTypography.current.caption, color = RefluxColors.textSecondary)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button("Remove", {
                                        removing = null
                                        app.removeDownload(line.id)
                                    })
                                    Button("Keep", { removing = null }, Modifier.focusRequester(answer), style = ButtonStyle.QUIET)
                                }
                            }
                        } else {
                            Line(line) {
                                when (line.state) {
                                    DownloadState.QUEUED, DownloadState.PREPARING, DownloadState.DOWNLOADING ->
                                        IconButton(Icon.PAUSE, "Pause the download of ${line.title}", { app.pauseDownload(line.id) }, size = 32.dp, tint = RefluxColors.textSecondary)
                                    DownloadState.PAUSED, DownloadState.FAILED, DownloadState.UNAVAILABLE ->
                                        IconButton(Icon.REFRESH, "Try the download of ${line.title} again", { app.resumeDownload(line.id) }, size = 32.dp, tint = RefluxColors.textSecondary)
                                    else -> Unit
                                }
                                // A copy that is not finished goes at once; one that is asks first.
                                if (line.state == DownloadState.COMPLETED || line.state == DownloadState.UNAVAILABLE) {
                                    IconButton(Icon.CLOSE, "Remove ${line.title} from this device", { removing = line.id }, size = 32.dp, tint = RefluxColors.textSecondary)
                                } else {
                                    IconButton(Icon.CLOSE, "Cancel the download of ${line.title}", { app.removeDownload(line.id) }, size = 32.dp, tint = RefluxColors.textSecondary)
                                }
                            }
                        }
                    }
                }
            }
            if (model.proposal == null && model.downloads.isEmpty() && reviewing == null) {
                Text(
                    "Nothing you are part-way through needs keeping right now. Use Download on a movie, an episode, or a season to keep it for later.",
                    style = LocalTypography.current.caption,
                    color = RefluxColors.textSecondary,
                )
            }
            // A season or a show kept episode by episode goes the same way, once, rather than one copy at a time.
            val kept = model.downloads.filter { it.state == DownloadState.COMPLETED }
            if (reviewing != null && kept.size > 1) {
                if (removingAll) {
                    Column(Modifier.pane("Remove these ${kept.size} episodes from this device?"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Remove these ${kept.size} episodes from this device?", style = LocalTypography.current.bodyStrong)
                        Text("Only the copies go. They stay in your library, with what you have watched.", style = LocalTypography.current.caption, color = RefluxColors.textSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button("Remove all", {
                                removingAll = false
                                app.removeDownloads(kept.map { it.id })
                            })
                            Button("Keep", { removingAll = false }, Modifier.focusRequester(answer), style = ButtonStyle.QUIET)
                        }
                    }
                } else {
                    Button("Remove all from this device", { removingAll = true }, style = ButtonStyle.QUIET)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (model.proposal != null) {
                Button(
                    if (model.proposed.size == 1) "Download" else "Download ${model.proposed.size}",
                    // Confirmed, the sheet goes: progress is beside what was chosen, and on the Downloads page.
                    {
                        if (reviewing != null) app.download(reviewing) else app.prepareOffline()
                        app.offlineOpen = false
                    },
                    Modifier.focusRequester(first),
                    icon = Icon.DOWNLOAD,
                    style = ButtonStyle.PRIMARY,
                )
                Button(if (reviewing != null) "Not now" else "Done", { app.offlineOpen = false }, style = ButtonStyle.QUIET)
            } else {
                Button("Done", { app.offlineOpen = false }, Modifier.focusRequester(first))
            }
        }
    }
}

@Composable
private fun Line(line: OfflineLine, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(line.title, style = LocalTypography.current.bodyStrong, maxLines = 1)
            line.caption?.let { Text(it, style = LocalTypography.current.caption, color = RefluxColors.textTertiary, maxLines = 1) }
            Text(line.detail, style = LocalTypography.current.caption, color = if (line.state == DownloadState.FAILED) RefluxColors.unavailable else RefluxColors.textSecondary, maxLines = 1)
            line.note?.let { Text(it, style = LocalTypography.current.caption, color = RefluxColors.textSecondary, maxLines = 2) }
            line.progress?.let { ProgressBar(it, Modifier.padding(top = 3.dp)) }
        }
        actions()
    }
}
