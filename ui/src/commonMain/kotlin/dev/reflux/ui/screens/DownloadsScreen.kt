package dev.reflux.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.reflux.core.offline.DownloadState
import dev.reflux.design.FormFactor
import dev.reflux.ui.components.ArtworkImage
import dev.reflux.ui.components.Button
import dev.reflux.ui.components.ButtonStyle
import dev.reflux.ui.components.Icon
import dev.reflux.ui.components.IconButton
import dev.reflux.ui.components.Pressable
import dev.reflux.ui.components.ProgressBar
import dev.reflux.ui.components.SectionHeader
import dev.reflux.ui.components.titleTones
import dev.reflux.ui.components.initialFocus
import dev.reflux.ui.components.rememberFocusReturn
import dev.reflux.ui.components.pane
import dev.reflux.ui.state.AppState
import dev.reflux.ui.state.Destination
import dev.reflux.ui.state.DownloadItem
import dev.reflux.ui.state.downloadsModel
import dev.reflux.ui.state.libraryState
import dev.reflux.ui.state.offlineState
import dev.reflux.ui.theme.LocalLayout
import dev.reflux.ui.theme.LocalTypography
import dev.reflux.ui.theme.RefluxColors
import dev.reflux.ui.theme.Text

/**
 * Every copy kept on this device, or on its way: what it is a copy of, which version, where it stands, and the
 * one or two things to do about it. Not a second library: a copy is played from its work's page, which a line
 * leads to, and nothing here is browsed.
 */
@Composable
internal fun DownloadsScreen(app: AppState) {
    val offline = app.platform.offline
    val loaded by app.offlineState { offline?.let { downloadsModel(it) } }
    // What Reflux would keep of what is being watched changes with watching, not with every megabyte.
    val suggestion by app.libraryState { offline?.suggest()?.empty == false }
    val model = loaded ?: return
    // The first line is where a remote starts; without any, the one action there is.
    val lines = model.active + model.stopped + model.completed
    val first = lines.firstOrNull()?.id
    var removing by remember { mutableStateOf<String?>(null) }
    // When a line goes, focus goes to the one after it (or before it), not off the page.
    var focusing by remember { mutableStateOf<String?>(null) }
    val removed: (String) -> Unit = { id ->
        val index = lines.indexOfFirst { it.id == id }
        focusing = (lines.getOrNull(index + 1) ?: lines.getOrNull(index - 1))?.id
    }
    val lineFocused: () -> Unit = { focusing = null }
    Page {
        item(key = "heading") { Heading("Downloads", model.summary) }
        if (suggestion == true) {
            item(key = "suggest") {
                Button(
                    "Keep what you're watching",
                    { app.offlineOpen = true },
                    if (first == null) Modifier.initialFocus() else Modifier,
                    icon = Icon.DOWNLOAD,
                )
            }
        }
        if (model.empty) {
            item(key = "empty") {
                Text(
                    "Nothing is downloaded. Use Download on a movie, an episode, or a season to keep it on this device and watch it without its source.",
                    color = RefluxColors.textTertiary,
                )
            }
        }
        val focus = LineFocus(first, focusing, removed, lineFocused)
        section("downloading", "Downloading", model.active, app, focus, removing) { removing = it }
        section("stopped", "Didn't finish", model.stopped, app, focus, removing) { removing = it }
        section("kept", "On this device", model.completed, app, focus, removing) { removing = it }
    }
}

/** Where focus starts on the page, where it goes after a line is removed, and how a removal is told. */
private class LineFocus(val first: String?, val focusing: String?, val removed: (String) -> Unit, val focused: () -> Unit)

private fun LazyListScope.section(
    key: String,
    title: String,
    items: List<DownloadItem>,
    app: AppState,
    focus: LineFocus,
    removing: String?,
    onRemoving: (String?) -> Unit,
) {
    if (items.isEmpty()) return
    item(key = "header-$key") { SectionHeader(title, Modifier.padding(top = 12.dp)) }
    items(items, key = { it.id }) { item ->
        if (removing == item.id) {
            RemoveQuestion(item, onRemove = { onRemoving(null); focus.removed(item.id); app.removeDownload(item.id) }, onKeep = { onRemoving(null) })
        } else {
            DownloadRow(app, item, focus, onRemove = { onRemoving(item.id) })
        }
    }
}

/**
 * A copy: its work's artwork and name, the version kept, where it stands, and beside it what can be done. On a
 * television the actions are a step to the right of the line, with room between them so a focused control's
 * ring stays clear of the line's text.
 */
@Composable
private fun DownloadRow(app: AppState, item: DownloadItem, focus: LineFocus, onRemove: () -> Unit) {
    val layout = LocalLayout.current
    val television = layout.formFactor == FormFactor.TV
    val phone = layout.formFactor == FormFactor.PHONE
    val shape = RoundedCornerShape(14.dp)
    // Back from the work's page comes back to this line.
    val back = rememberFocusReturn("download/${item.id}")
    val requester = remember { FocusRequester() }
    if (focus.focusing == item.id && layout.showsFocus) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { requester.requestFocus() }
            focus.focused()
        }
    }
    val first = focus.first == item.id
    // Giving up a line that is not finished goes at once: focus moves on first.
    val gone: () -> Unit = { focus.removed(item.id); app.removeDownload(item.id) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (television) 24.dp else 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Pressable(
            { back.leaving(); app.navigator.open(Destination.Details(item.itemId)) },
            Modifier.weight(1f).then(back.modifier).focusRequester(requester).then(if (first) Modifier.initialFocus() else Modifier),
            shape,
            lift = 0.15f,
        ) { active ->
            Row(
                Modifier.fillMaxWidth().background(Color.White.copy(alpha = if (active) 0.07f else 0f)).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val poster = RoundedCornerShape(8.dp)
                val width = if (phone) 44.dp else 52.dp
                Box(Modifier.width(width).aspectRatio(2f / 3f).clip(poster)) {
                    // Too small for a title of its own, and the title is beside it: the work's colors stand in.
                    ArtworkImage(item.poster, width, Modifier.fillMaxSize()) {
                        val (light, dark) = titleTones(item.title)
                        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(light, dark))))
                    }
                    Box(Modifier.fillMaxSize().border(1.dp, Color.White.copy(alpha = 0.07f), poster))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(item.title, style = LocalTypography.current.bodyStrong, maxLines = 1)
                    item.caption?.let { Text(it, style = LocalTypography.current.caption, color = RefluxColors.textSecondary, maxLines = 1) }
                    if (item.version.isNotEmpty()) Text(item.version, style = LocalTypography.current.caption, color = RefluxColors.textTertiary, maxLines = 1)
                    // Under "On this device" a kept copy needs no line saying so.
                    if (item.state != DownloadState.COMPLETED) {
                        Text(
                            item.status,
                            style = LocalTypography.current.caption,
                            color = if (item.state == DownloadState.FAILED || item.state == DownloadState.UNAVAILABLE) RefluxColors.unavailable else RefluxColors.textSecondary,
                            maxLines = 1,
                        )
                    }
                    item.progress?.let { ProgressBar(it, Modifier.padding(top = 3.dp)) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(if (television) 12.dp else 4.dp), verticalAlignment = Alignment.CenterVertically) {
            val name = listOfNotNull(item.title, item.caption).joinToString(", ")
            when (item.state) {
                DownloadState.QUEUED, DownloadState.PREPARING, DownloadState.DOWNLOADING -> {
                    IconButton(Icon.PAUSE, "Pause the download of $name", { app.pauseDownload(item.id) }, tint = RefluxColors.textSecondary)
                    IconButton(Icon.CLOSE, "Cancel the download of $name", gone, tint = RefluxColors.textSecondary)
                }
                DownloadState.PAUSED -> {
                    IconButton(Icon.DOWNLOAD, "Continue the download of $name", { app.resumeDownload(item.id) }, tint = RefluxColors.textSecondary)
                    IconButton(Icon.CLOSE, "Cancel the download of $name", gone, tint = RefluxColors.textSecondary)
                }
                DownloadState.FAILED, DownloadState.UNAVAILABLE -> {
                    IconButton(Icon.REFRESH, "Try the download of $name again", { app.resumeDownload(item.id) }, tint = RefluxColors.textSecondary)
                    // What a stopped download has is kept for trying again; giving it up is removing it.
                    IconButton(Icon.CLOSE, "Remove the download of $name", gone, tint = RefluxColors.textSecondary)
                }
                // A finished copy is only removed when the user says so twice.
                DownloadState.COMPLETED -> IconButton(Icon.CLOSE, "Remove $name from this device", onRemove, tint = RefluxColors.textSecondary)
                DownloadState.CANCELLED -> Unit
            }
        }
    }
}

/**
 * Removing a finished copy asks once, saying what goes and what stays. With a remote, focus starts on Keep, so
 * that a press too many keeps the copy rather than removing it.
 */
@Composable
private fun RemoveQuestion(item: DownloadItem, onRemove: () -> Unit, onKeep: () -> Unit) {
    val answer = remember { FocusRequester() }
    val showsFocus = LocalLayout.current.showsFocus
    LaunchedEffect(Unit) { if (showsFocus) runCatching { answer.requestFocus() } }
    val question = "Remove ${listOfNotNull(item.title, item.caption).joinToString(", ")} from this device?"
    Column(Modifier.fillMaxWidth().pane(question).padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(question, style = LocalTypography.current.bodyStrong)
        Text("Only the copy goes. It stays in your library, with what you have watched.", style = LocalTypography.current.caption, color = RefluxColors.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button("Remove", onRemove)
            Button("Keep", onKeep, Modifier.focusRequester(answer), style = ButtonStyle.QUIET)
        }
    }
}
