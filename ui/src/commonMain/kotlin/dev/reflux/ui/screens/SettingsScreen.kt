package dev.reflux.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.reflux.core.profile.PinPolicy
import dev.reflux.design.MaterialRole
import dev.reflux.ui.components.Button
import dev.reflux.ui.components.ButtonStyle
import dev.reflux.ui.components.Icon
import dev.reflux.ui.components.InputKind
import dev.reflux.ui.components.TextInput
import dev.reflux.ui.components.heading
import dev.reflux.ui.components.initialFocus
import dev.reflux.ui.components.rememberFocusReturn
import dev.reflux.ui.state.AppState
import dev.reflux.ui.state.Destination
import dev.reflux.ui.theme.LocalTypography
import dev.reflux.ui.theme.RefluxColors
import dev.reflux.ui.theme.Shapes
import dev.reflux.ui.theme.Text
import dev.reflux.ui.theme.glass

/**
 * Settings (docs/NAVIGATION.md, section 1): first the open profile's own (its name, its PIN, clearing its watch
 * history), then what is about this device (online lookups, Media Atmosphere), then About. Few on purpose: Reflux
 * is meant to be right without any of them (AGENTS.md, "Excellent out of the box").
 */
@Composable
internal fun SettingsScreen(app: AppState) {
    LaunchedEffect(Unit) { app.loadProfiles() }
    val profile = app.activeProfile
    var renaming by remember { mutableStateOf(false) }
    var settingPin by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    Page {
        item(key = "heading") { Heading("Settings", null) }
        item(key = "profile-title") { Section(if (app.profilesShown) profile?.name ?: "This profile" else "You") }
        item(key = "name") {
            if (renaming) {
                var name by remember { mutableStateOf(profile?.name.orEmpty()) }
                Column(Modifier.fillMaxWidth().glass(MaterialRole.ELEVATED, Shapes.large).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextInput(name, { name = it.take(40) }, "Name", Modifier.initialFocus(), last = true, onDone = { renaming = false; app.renameProfile(name) })
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button("Save", { renaming = false; app.renameProfile(name) }, style = ButtonStyle.PRIMARY, enabled = name.isNotBlank())
                        Button("Cancel", { renaming = false }, style = ButtonStyle.QUIET)
                    }
                }
            } else {
                Option(Icon.PROFILE, "Name", profile?.name ?: "", Modifier.initialFocus()) { renaming = true }
            }
        }
        if (app.pinsAvailable) {
            item(key = "pin") {
                when {
                    settingPin -> PinSetup(onSave = { pin -> settingPin = false; app.setPin(pin) }, onCancel = { settingPin = false })
                    profile?.hasPin == true -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Option(Icon.LOCK, "Change PIN", "This profile asks for its PIN when it is opened.") { settingPin = true }
                        Button("Remove PIN", { app.removePin() }, style = ButtonStyle.QUIET)
                    }
                    else -> Option(Icon.LOCK, "Add a PIN", "Ask for a ${PinPolicy.LENGTH}-digit PIN before this profile opens. It keeps your profile private on a shared device.") { settingPin = true }
                }
            }
        }
        item(key = "clear") {
            if (clearing) {
                Confirm(
                    "Clear watch history on this device?",
                    "Reflux forgets what this profile watched and where it stopped, and Continue Watching starts empty. Nothing changes on your servers, which keep their own history, and your media, downloads, favorites, and other profiles stay as they are.",
                    confirm = "Clear history",
                    onConfirm = { clearing = false; app.clearWatchHistory() },
                    onCancel = { clearing = false },
                )
            } else {
                Option(Icon.REFRESH, "Clear watch history", "Forget what this profile watched on this device. Your servers keep their own history.") { clearing = true }
            }
        }

        item(key = "device-title") { Section("This device") }
        // Whether titles, descriptions, and artwork may be looked up online: a privacy choice for the device, so it is
        // the viewer's to make. Only where the build has a provider to ask (docs/METADATA.md, "Online lookups").
        if (app.hasMetadataProvider) {
            item(key = "online") {
                SwitchRow(
                    "Online metadata & artwork",
                    "Look up titles, descriptions, and artwork by name. Off, Reflux uses what your sources provide and what it already has.",
                    app.onlineMetadata,
                ) { app.setOnlineMetadata(!app.onlineMetadata) }
            }
        }
        // The one appearance choice Reflux has: whether artwork may color the space around it. Off by default; it is
        // not a theme, and nothing else about the interface changes with it (DECISIONS.md, 2026-11-07).
        item(key = "atmosphere") {
            SwitchRow("Media Atmosphere", "Let artwork tint the space around it, on Home and on a title's page.", app.mediaAtmosphere) {
                app.setMediaAtmosphere(!app.mediaAtmosphere)
            }
        }
        // Reflux itself: its version, privacy, credits, and help.
        item(key = "about") {
            val back = rememberFocusReturn("settings/about")
            Option(Icon.INFO, "About Reflux", "Version ${app.platform.build.version}, privacy, help, licenses, and credits", back.modifier.padding(top = 12.dp)) {
                back.leaving()
                app.navigator.open(Destination.About)
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, Modifier.padding(top = 14.dp, bottom = 2.dp).heading(), style = LocalTypography.current.title)
}

/** A new PIN, entered twice so a slip of the finger does not lock the viewer out. */
@Composable
private fun PinSetup(onSave: (String) -> Unit, onCancel: () -> Unit) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    val ready = PinPolicy.valid(first) && first == second
    val mismatch = second.length == PinPolicy.LENGTH && first != second
    Column(Modifier.fillMaxWidth().glass(MaterialRole.ELEVATED, Shapes.large).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextInput(first, { first = it.filter(Char::isDigit).take(PinPolicy.LENGTH) }, "New PIN", Modifier.widthIn(max = 220.dp).initialFocus(), kind = InputKind.PIN)
            TextInput(second, { second = it.filter(Char::isDigit).take(PinPolicy.LENGTH) }, "Again", Modifier.widthIn(max = 220.dp), kind = InputKind.PIN, last = true, error = mismatch, onDone = { if (ready) onSave(first) })
        }
        if (mismatch) Text("The two don't match.", style = LocalTypography.current.caption, color = RefluxColors.contentSecondary)
        Text(
            "Forgetting it can't be undone by Reflux: another profile can delete this one, and with it what it kept on this device.",
            style = LocalTypography.current.caption,
            color = RefluxColors.contentTertiary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button("Save PIN", { onSave(first) }, style = ButtonStyle.PRIMARY, enabled = ready)
            Button("Cancel", onCancel, style = ButtonStyle.QUIET)
        }
    }
}
