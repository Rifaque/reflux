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
import dev.reflux.ui.components.announces
import dev.reflux.ui.components.pane
import dev.reflux.ui.components.initialFocus
import dev.reflux.ui.components.readable
import androidx.compose.ui.semantics.semantics
import dev.reflux.ui.components.rememberFocusReturn
import dev.reflux.ui.components.rememberPanelReturn
import dev.reflux.ui.components.returnsFocus
import dev.reflux.ui.components.takesFocus
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
    var removingPin by remember { mutableStateOf(false) }
    // Focus comes back to what opened a panel in place, when it closes (for input that shows focus).
    val nameReturn = rememberPanelReturn()
    val pinReturn = rememberPanelReturn()
    val clearReturn = rememberPanelReturn()
    val closePin = { settingPin = false; removingPin = false; app.dismissPinRefusal(); pinReturn.back() }
    var clearing by remember { mutableStateOf(false) }
    Page {
        item(key = "heading") { Heading("Settings", null) }
        item(key = "profile-title") { Section(if (app.profilesShown) profile?.name ?: "This profile" else "You") }
        item(key = "name") {
            if (renaming) {
                ClosesOnBack(app) { renaming = false; nameReturn.back() }
                var name by remember { mutableStateOf(profile?.name.orEmpty()) }
                Column(Modifier.fillMaxWidth().glass(MaterialRole.ELEVATED, Shapes.large).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextInput(name, { name = it.take(40) }, "Name", Modifier.initialFocus().takesFocus(), last = true, onDone = { renaming = false; nameReturn.back(); app.renameProfile(name) })
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button("Save", { renaming = false; nameReturn.back(); app.renameProfile(name) }, style = ButtonStyle.PRIMARY, enabled = name.isNotBlank())
                        Button("Cancel", { renaming = false; nameReturn.back() }, style = ButtonStyle.QUIET)
                    }
                }
            } else {
                Option(Icon.PROFILE, "Name", profile?.name ?: "", Modifier.initialFocus().returnsFocus(nameReturn)) { renaming = true }
            }
        }
        if (app.pinsAvailable) {
            item(key = "pin") {
                // Changing or removing a PIN needs that PIN (B-049): an unlocked device is not a way to take a profile's
                // protection. Once it has gone, the panel closes by itself.
                LaunchedEffect(profile?.hasPin) { if (profile?.hasPin != true && removingPin) closePin() }
                when {
                    settingPin -> PinPanel(app, closePin) {
                        PinSetup(
                            changing = profile?.hasPin == true,
                            refused = app.pinRefused,
                            onSave = { current, pin ->
                                if (profile?.hasPin == true) app.setPin(pin, current).invokeOnCompletion { if (app.pinRefused == null) { settingPin = false; pinReturn.back() } }
                                else { settingPin = false; pinReturn.back(); app.setPin(pin) }
                            },
                            onCancel = closePin,
                        )
                    }
                    removingPin -> PinPanel(app, closePin) {
                        RemovePin(profile?.name ?: "This profile", app.pinRefused, onRemove = { current -> app.removePin(current) }, onKeep = closePin)
                    }
                    profile?.hasPin == true -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Option(Icon.LOCK, "Change PIN", "This profile asks for its PIN when it is opened.", Modifier.returnsFocus(pinReturn)) { settingPin = true }
                        Option(Icon.LOCK, "Remove PIN", "Open this profile without a PIN. You'll need the current PIN.") { removingPin = true }
                    }
                    else -> Option(Icon.LOCK, "Add a PIN", "Ask for a ${PinPolicy.LENGTH}-digit PIN before this profile opens. It keeps your profile private on a shared device.", Modifier.returnsFocus(pinReturn)) { settingPin = true }
                }
            }
        }
        item(key = "clear") {
            if (clearing) {
                Confirm(
                    app,
                    "Clear watch history on this device?",
                    "Reflux forgets what this profile watched and where it stopped, and Continue Watching starts empty. Nothing changes on your servers, which keep their own history, and your media, downloads, favorites, and other profiles stay as they are.",
                    confirm = "Clear history",
                    onConfirm = { clearing = false; clearReturn.back(); app.clearWatchHistory() },
                    onCancel = { clearing = false; clearReturn.back() },
                )
            } else {
                Option(Icon.REFRESH, "Clear watch history", "Forget what this profile watched on this device. Your servers keep their own history.", Modifier.returnsFocus(clearReturn)) { clearing = true }
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
        } else {
            // A build without a provider (a development build, say) says so, where the switch would be, instead of
            // leaving placeholders unexplained. Not a switch, and nothing to do: a sentence to read, which a remote,
            // a keyboard, or a screen reader can reach like any other row.
            item(key = "online-unavailable") {
                Text(
                    ONLINE_METADATA_UNAVAILABLE,
                    Modifier.fillMaxWidth().readable(Shapes.medium).semantics(mergeDescendants = true) {}.padding(horizontal = 14.dp, vertical = 11.dp),
                    style = LocalTypography.current.caption,
                    color = RefluxColors.contentSecondary,
                )
            }
        }
        // What is felt (F-001, F-002): offered only where it means something. Interface haptics are off until turned
        // on; controller rumble is on until turned off.
        if (app.haptics.interfaceAvailable) {
            item(key = "interface-haptics") {
                SwitchRow(
                    "Interface haptics",
                    "A light tap from this device for a few things you do by touch: a switch changing, a pull to refresh, a wrong PIN. Off until you turn it on.",
                    app.haptics.interfaceOn,
                ) { app.setInterfaceHaptics(!app.haptics.interfaceOn) }
            }
        }
        if (app.platform.controllerRumble != null) {
            item(key = "controller-rumble") {
                SwitchRow(
                    "Controller rumble",
                    "A light tick from a controller that has motors as you move and choose, a firmer one at the end of a row, and one when something is refused. On until you turn it off.",
                    app.haptics.rumbleOn,
                ) { app.setControllerRumble(!app.haptics.rumbleOn) }
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

/** Settings' sentence for a build that has no metadata provider to ask. */
internal const val ONLINE_METADATA_UNAVAILABLE = "Online metadata is unavailable in this build."

@Composable
private fun Section(title: String) {
    Text(title, Modifier.padding(top = 14.dp, bottom = 2.dp).heading(), style = LocalTypography.current.title)
}

/**
 * A new PIN, entered twice so a slip of the finger does not lock the viewer out. Replacing one ([changing]) asks for the
 * current PIN first (B-049); [refused] says why the last one given was not accepted.
 */
/** A PIN panel, which Back closes. */
@Composable
private fun PinPanel(app: AppState, close: () -> Unit, content: @Composable () -> Unit) {
    ClosesOnBack(app, close)
    content()
}

@Composable
private fun PinSetup(changing: Boolean, refused: dev.reflux.ui.state.PinRefusal?, onSave: (current: String?, next: String) -> Unit, onCancel: () -> Unit) {
    var current by remember(refused?.attempt) { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    val ready = PinPolicy.valid(first) && first == second && (!changing || PinPolicy.valid(current))
    val mismatch = second.length == PinPolicy.LENGTH && first != second
    Column(Modifier.fillMaxWidth().glass(MaterialRole.ELEVATED, Shapes.large).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (changing) {
                TextInput(current, { current = it.filter(Char::isDigit).take(PinPolicy.LENGTH) }, "Current PIN", Modifier.widthIn(max = 220.dp).initialFocus().takesFocus(), kind = InputKind.PIN, error = refused != null)
            }
            TextInput(first, { first = it.filter(Char::isDigit).take(PinPolicy.LENGTH) }, "New PIN", Modifier.widthIn(max = 220.dp).then(if (changing) Modifier else Modifier.initialFocus().takesFocus()), kind = InputKind.PIN)
            TextInput(second, { second = it.filter(Char::isDigit).take(PinPolicy.LENGTH) }, "Again", Modifier.widthIn(max = 220.dp), kind = InputKind.PIN, last = true, error = mismatch, onDone = { if (ready) onSave(current.takeIf { changing }, first) })
        }
        refused?.problem?.let { Text(it, Modifier.announces(it), style = LocalTypography.current.caption, color = RefluxColors.contentSecondary) }
        if (mismatch) Text("The two don't match.", style = LocalTypography.current.caption, color = RefluxColors.contentSecondary)
        Text(
            "Forgetting it can't be undone by Reflux: another profile can delete this one, and with it what it kept on this device.",
            style = LocalTypography.current.caption,
            color = RefluxColors.contentTertiary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button("Save PIN", { onSave(current.takeIf { changing }, first) }, style = ButtonStyle.PRIMARY, enabled = ready)
            Button("Cancel", onCancel, style = ButtonStyle.QUIET)
        }
    }
}

/**
 * Removing the open profile's PIN (B-049): what it means, said plainly; the current PIN, without which nothing changes;
 * and the safe answer first. A wrong PIN counts as a try, as opening the profile does.
 */
@Composable
private fun RemovePin(name: String, refused: dev.reflux.ui.state.PinRefusal?, onRemove: (String) -> Unit, onKeep: () -> Unit) {
    var current by remember(refused?.attempt) { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().glass(MaterialRole.ELEVATED, Shapes.large).padding(16.dp).pane("Remove PIN", onKeep), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Remove $name's PIN?", Modifier.heading(), style = LocalTypography.current.bodyStrong)
        Text(
            "$name will open without a PIN. Anyone using this device can then see its watch history, favorites, and downloads, and use its server sign-ins. Its history and settings stay as they are.",
            style = LocalTypography.current.caption,
            color = RefluxColors.contentSecondary,
        )
        TextInput(
            current, { current = it.filter(Char::isDigit).take(PinPolicy.LENGTH) }, "Current PIN", Modifier.widthIn(max = 220.dp),
            kind = InputKind.PIN, last = true, error = refused != null, onDone = { if (PinPolicy.valid(current)) onRemove(current) },
        )
        refused?.problem?.let { Text(it, Modifier.announces(it), style = LocalTypography.current.caption, color = RefluxColors.contentSecondary) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button("Keep PIN", onKeep, Modifier.initialFocus().takesFocus(), style = ButtonStyle.QUIET)
            Button("Remove PIN", { onRemove(current) }, style = ButtonStyle.GLASS, enabled = PinPolicy.valid(current))
        }
    }
}
