package dev.reflux.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.reflux.design.MaterialRole
import dev.reflux.ui.components.Button
import dev.reflux.ui.components.ButtonStyle
import dev.reflux.ui.components.Icon
import dev.reflux.ui.components.Pressable
import dev.reflux.ui.components.absorbsPointer
import dev.reflux.ui.components.focusPanel
import dev.reflux.ui.state.BrowsedFolder
import dev.reflux.ui.state.FolderListing
import dev.reflux.ui.state.FolderPicker
import dev.reflux.ui.theme.LocalLayout
import dev.reflux.ui.theme.LocalTypography
import dev.reflux.ui.theme.RefluxColors
import dev.reflux.ui.theme.Text
import dev.reflux.ui.theme.glass

/**
 * Choosing a media folder where the platform offers no picker (a television): one folder at a time, with
 * whatever is in the user's hand. The open folder can be used as it is, or one of its folders gone into;
 * Back goes up again. Focus stays inside until a folder is chosen or the picker is closed.
 */
@Composable
internal fun FolderPickerPanel(picker: FolderPicker) {
    val current = picker.current
    val folders = picker.folders
    val main = remember { FocusRequester() }
    val showsFocus = LocalLayout.current.showsFocus
    // Each step starts on what is most likely wanted next: this folder, or the first place to look.
    LaunchedEffect(picker.path.size) {
        if (!showsFocus) return@LaunchedEffect
        withFrameNanos { }
        runCatching { main.requestFocus() }
    }
    Column(
        Modifier.widthIn(max = 520.dp).fillMaxWidth().glass(MaterialRole.MODAL).absorbsPointer()
            .focusPanel().focusProperties { onExit = { cancelFocusChange() } }.focusGroup()
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (current == null) "Where is your media?" else current.folder.name, style = LocalTypography.current.title, maxLines = 1)
            Text(
                if (current == null) "Choose where it is stored, then the folder it is in. Reflux only reads it."
                else (picker.path.dropLast(1).map { it.name } + contents(current.listing)).joinToString("  ›  "),
                style = LocalTypography.current.caption,
                color = RefluxColors.textSecondary,
                maxLines = 2,
            )
        }
        if (current != null) {
            Button("Use this folder", { picker.choose() }, Modifier.focusRequester(main), icon = Icon.CHECK, style = ButtonStyle.SOLID, enabled = !picker.busy)
        }
        if (folders.isNotEmpty()) {
            LazyColumn(Modifier.heightIn(max = 236.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                itemsIndexed(folders, key = { _, folder -> folder.id }) { index, folder ->
                    FolderRow(folder, if (current == null && index == 0) Modifier.focusRequester(main) else Modifier, enabled = !picker.busy) { picker.open(folder) }
                }
            }
        }
    }
}

/** What is directly in a folder, in a few words. */
private fun contents(listing: FolderListing): String {
    fun count(number: Int, one: String, many: String) = if (number == 1) "1 $one" else "$number $many"
    val parts = listOfNotNull(
        count(listing.videos, "video", "videos").takeIf { listing.videos > 0 },
        count(listing.folders.size, "folder", "folders").takeIf { listing.folders.isNotEmpty() },
    )
    return if (parts.isEmpty()) "Nothing to play in here" else parts.joinToString(", ")
}

@Composable
private fun FolderRow(folder: BrowsedFolder, modifier: Modifier, enabled: Boolean, onOpen: () -> Unit) {
    Pressable(onOpen, modifier.fillMaxWidth(), RoundedCornerShape(12.dp), enabled = enabled, lift = 0f) { active ->
        Row(
            Modifier.fillMaxWidth().background(Color.White.copy(alpha = if (active) 0.10f else 0f)).padding(horizontal = 14.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icon.FOLDER, size = 18.dp, tint = RefluxColors.textSecondary)
            Text(folder.name, Modifier.weight(1f), style = LocalTypography.current.bodyStrong, maxLines = 1)
            Icon(Icon.CHEVRON_RIGHT, size = 14.dp, tint = RefluxColors.textTertiary)
        }
    }
}
