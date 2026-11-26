package dev.reflux.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.reflux.core.profile.PinPolicy
import dev.reflux.design.MaterialRole
import dev.reflux.library.Profile
import dev.reflux.ui.components.Button
import dev.reflux.ui.components.ButtonStyle
import dev.reflux.ui.components.Icon
import dev.reflux.ui.components.InputKind
import dev.reflux.ui.components.Pressable
import dev.reflux.ui.components.TextInput
import dev.reflux.ui.components.announces
import dev.reflux.ui.components.absorbsPointer
import dev.reflux.ui.components.heading
import dev.reflux.ui.components.initialFocus
import dev.reflux.ui.components.pane
import dev.reflux.ui.components.rememberPanelReturn
import dev.reflux.ui.components.returnsFocus
import dev.reflux.ui.components.takesFocus
import dev.reflux.ui.state.AppState
import dev.reflux.ui.state.Destination
import dev.reflux.ui.state.ProfileGate
import dev.reflux.ui.theme.LocalLayout
import dev.reflux.ui.theme.LocalTypography
import dev.reflux.ui.theme.RefluxColors
import dev.reflux.ui.theme.Shapes
import dev.reflux.ui.theme.States
import dev.reflux.ui.theme.Text
import dev.reflux.ui.theme.glass

/**
 * A profile's mark: its initial in a neutral circle (docs/product/DESIGN.md: the interface takes no color of its own). The
 * active profile's is lit; what tells profiles apart is the name beside it, never a color.
 */
@Composable
internal fun ProfileMark(profile: Profile?, size: Dp = 40.dp, lit: Boolean = false) {
    val initial = profile?.name?.trim()?.firstOrNull()?.uppercaseChar()?.toString()
    Box(
        Modifier.size(size).background(States.layer(if (lit) 0.9f else 0.12f), CircleShape).border(1.dp, States.layer(0.18f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (initial != null) {
            // Beside the navigation's icons the mark is icon-sized, and so is its letter: about half the circle, never the
            // body text's size crammed into it (the owner's walk, 2026-11-20).
            val typography = LocalTypography.current
            Text(
                initial,
                style = when {
                    size >= 56.dp -> typography.title
                    size >= 40.dp -> typography.bodyStrong
                    else -> typography.bodyStrong.copy(fontSize = androidx.compose.ui.unit.TextUnit(size.value * 0.46f, androidx.compose.ui.unit.TextUnitType.Sp), lineHeight = androidx.compose.ui.unit.TextUnit(size.value * 0.6f, androidx.compose.ui.unit.TextUnitType.Sp))
                },
                color = if (lit) RefluxColors.background else RefluxColors.contentPrimary,
            )
        } else {
            Icon(Icon.PROFILE, size = size * 0.5f, tint = if (lit) RefluxColors.background else RefluxColors.contentPrimary)
        }
    }
}

/**
 * Who is watching (docs/product/NAVIGATION.md, section 1): the profile entry of the utility area. The profiles of this
 * installation, switching between them, adding one, and deleting one that is not open. On a phone, where the utility
 * area is this one entry, it also leads to Downloads, Sources, and Settings.
 */
@Composable
internal fun ProfilesScreen(app: AppState) {
    LaunchedEffect(Unit) { app.loadProfiles() }
    // A phone, upright or on its side: its utility area is this one entry.
    val phone = LocalLayout.current.navigation.let { it == dev.reflux.design.NavigationStyle.BOTTOM_BAR || it == dev.reflux.design.NavigationStyle.COMPACT_RAIL }
    var adding by remember { mutableStateOf(false) }
    // One profile is managed at a time, in a panel under its row (the owner's walk, 2026-11-20): its own actions with it,
    // never a list of every profile's Delete appended below them all.
    var managing by remember { mutableStateOf<Any?>(null) }
    // Focus comes back to what opened a panel in place, when it closes (for input that shows focus).
    val addReturn = rememberPanelReturn()
    val manageReturn = rememberPanelReturn()
    // Once the profile is gone, its panel is too.
    LaunchedEffect(app.profiles) {
        if (managing != null && app.profiles.none { it.id == managing }) {
            managing = null
            manageReturn.back()
        }
    }
    Page {
        item(key = "heading") {
            Heading(
                if (app.profilesShown) "Who's watching" else "Profile",
                if (app.profilesShown) "Each profile has its own watch history, favorites, collections, and server accounts." else "Add a profile for each person who watches here. Each keeps their own history, favorites, and server accounts.",
            )
        }
        app.profiles.forEachIndexed { index, profile ->
            item(key = "profile/${profile.id.value}") {
                val open = managing == profile.id
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProfileRow(
                        profile,
                        if (index == 0) Modifier.initialFocus() else Modifier,
                        manage = ManageButton(open, if (open) manageReturn else null) { managing = if (open) null else profile.id },
                    ) { app.chooseProfile(profile) }
                    if (open) ManageProfile(app, profile, onClose = { managing = null; app.deletionRefused = null; manageReturn.back() })
                }
            }
        }
        // Leaving the open profile without choosing another yet (B-017): Reflux then asks who is watching.
        if (app.profilesShown) {
            item(key = "log-out") {
                Button("Log out of ${app.activeProfile?.name ?: "this profile"}", { app.logOut() }, Modifier.padding(top = 4.dp), icon = Icon.LOCK, style = ButtonStyle.QUIET)
            }
        }
        // Adding is apart from any one profile's management.
        item(key = "add") {
            if (adding) {
                ClosesOnBack(app) { adding = false; addReturn.back() }
                AddProfile(onAdd = { name -> adding = false; addReturn.back(); app.addProfile(name) }, onCancel = { adding = false; addReturn.back() })
            } else {
                Button("Add a profile", { adding = true }, Modifier.padding(top = 4.dp).returnsFocus(addReturn), icon = Icon.PLUS, style = ButtonStyle.QUIET)
            }
        }
        // A phone's utility area is this one entry: the rest of it is reached from here.
        if (phone) {
            item(key = "utility") { Text("More", Modifier.padding(top = 18.dp).heading(), style = LocalTypography.current.title) }
            if (Destination.Downloads in app.utilityDestinations) {
                item(key = "downloads") { Option(Icon.DOWNLOAD, "Downloads", "Everything kept on this device for watching offline.") { app.go(Destination.Downloads) } }
            }
            item(key = "sources") { Option(Icon.SOURCES, "Sources", "Folders, servers, and shares your library comes from.") { app.go(Destination.Sources) } }
            item(key = "settings") { Option(Icon.SETTINGS, "Settings", "This profile, online lookups, appearance, and About Reflux.") { app.go(Destination.Settings) } }
        }
    }
}

/** A profile row's own management button: open or closed, and where focus returns when its panel closes. */
private class ManageButton(val open: Boolean, val returns: dev.reflux.ui.components.PanelReturn?, val toggle: () -> Unit)

@Composable
private fun ProfileRow(profile: Profile, modifier: Modifier = Modifier, manage: ManageButton? = null, onClick: () -> Unit) {
    if (manage == null) return ProfileCard(profile, modifier, onClick)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        ProfileCard(profile, modifier.weight(1f), onClick)
        dev.reflux.ui.components.IconButton(
            Icon.MORE,
            if (manage.open) "Close ${profile.name}'s options" else "Manage ${profile.name}",
            manage.toggle,
            manage.returns?.let { Modifier.returnsFocus(it) } ?: Modifier,
            size = 44.dp,
            glass = true,
            selected = manage.open,
        )
    }
}

/**
 * What can be done with one profile, under its row. The open profile: its name, changed here, and its PIN, in Settings,
 * where it is set and changed with the current one; it is not deleted while open (switch first), so Reflux is never left
 * without one. Another profile: deleted, after a confirmation, and with its own PIN where it has one (docs/product/WATCH_STATE.md,
 * "Profiles"). Its name and PIN are its owner's, changed from within it.
 */
@Composable
private fun ManageProfile(app: AppState, profile: Profile, onClose: () -> Unit) {
    var renaming by remember(profile.id) { mutableStateOf(false) }
    var confirming by remember(profile.id) { mutableStateOf(false) }
    var askingPin by remember(profile.id) { mutableStateOf(false) }
    val backOut = {
        when {
            renaming -> renaming = false
            confirming || askingPin -> { confirming = false; askingPin = false; app.deletionRefused = null }
            else -> onClose()
        }
    }
    if (!confirming) ClosesOnBack(app, backOut)
    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp).glass(MaterialRole.ELEVATED, Shapes.large).padding(16.dp).pane("${profile.name}, options", onClose),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when {
            renaming -> {
                var name by remember { mutableStateOf(profile.name) }
                val save = { if (name.isNotBlank()) { renaming = false; app.renameProfile(name.trim()) } }
                TextInput(name, { name = it.take(40) }, "Name", Modifier.initialFocus().takesFocus(), last = true, onDone = { save() })
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button("Save", { save() }, style = ButtonStyle.PRIMARY, enabled = name.isNotBlank())
                    Button("Cancel", { renaming = false }, style = ButtonStyle.QUIET)
                }
            }
            askingPin -> DeletionPin(app, profile, onCancel = { askingPin = false; confirming = false; app.deletionRefused = null })
            confirming -> Confirm(
                app,
                "Delete ${profile.name}?",
                "Its watch history, favorites, collections, and downloads go, and its server accounts are signed out of on this device. Your servers keep their own history.",
                confirm = "Delete",
                onConfirm = { if (profile.hasPin) askingPin = true else { confirming = false; app.deleteProfile(profile) } },
                onCancel = { confirming = false },
            )
            profile.active -> {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button("Rename", { renaming = true }, Modifier.initialFocus().takesFocus(), icon = Icon.PROFILE, style = ButtonStyle.QUIET)
                    Button(if (profile.hasPin) "Change PIN" else "Add a PIN", { app.go(Destination.Settings) }, icon = Icon.LOCK, style = ButtonStyle.QUIET)
                }
                Text(
                    if (app.profiles.size > 1) "This profile is open. To delete it, switch to another profile first." else "Reflux keeps at least one profile, so this one can't be deleted.",
                    style = LocalTypography.current.caption,
                    color = RefluxColors.contentTertiary,
                )
            }
            else -> {
                Button("Delete ${profile.name}…", { confirming = true }, Modifier.initialFocus().takesFocus(), style = ButtonStyle.QUIET)
                Text(
                    if (profile.hasPin) "Its name and PIN are changed from within it. Deleting it asks for its PIN." else "Its name and PIN are changed from within it.",
                    style = LocalTypography.current.caption,
                    color = RefluxColors.contentTertiary,
                )
            }
        }
    }
}

@Composable
private fun ProfileCard(profile: Profile, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = Shapes.large
    Pressable(
        onClick,
        modifier.fillMaxWidth().semantics {
            selected = profile.active
            stateDescription = listOfNotNull(if (profile.active) "Watching now" else null, if (profile.hasPin) "Asks for a PIN" else null).joinToString(", ")
        },
        shape,
        lift = 0.1f,
        role = Role.Button,
    ) { active ->
        Row(
            Modifier.fillMaxWidth().glass(MaterialRole.ELEVATED, shape).background(States.layer(if (active) 0.07f else 0f)).padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProfileMark(profile, 44.dp, lit = profile.active)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(profile.name, style = LocalTypography.current.bodyStrong, maxLines = 1)
                if (profile.active) Text("Watching now", style = LocalTypography.current.caption, color = RefluxColors.contentTertiary, maxLines = 1)
            }
            if (profile.hasPin) Icon(Icon.LOCK, size = 18.dp, tint = RefluxColors.contentTertiary)
        }
    }
}

@Composable
private fun AddProfile(onAdd: (String) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().glass(MaterialRole.ELEVATED, Shapes.large).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextInput(name, { name = it.take(40) }, "Name", Modifier.initialFocus().takesFocus(), last = true, onDone = { if (name.isNotBlank()) onAdd(name) })
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button("Add", { onAdd(name) }, style = ButtonStyle.PRIMARY, enabled = name.isNotBlank())
            Button("Cancel", onCancel, style = ButtonStyle.QUIET)
        }
    }
}

/** A question that changes something that cannot be undone, said plainly, with the safe answer in reach first. */
@Composable
internal fun Confirm(app: AppState, title: String, body: String, confirm: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    ClosesOnBack(app, onCancel)
    Column(Modifier.fillMaxWidth().glass(MaterialRole.ELEVATED, Shapes.large).padding(16.dp).pane(title, onCancel), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, Modifier.heading(), style = LocalTypography.current.bodyStrong)
        Text(body, style = LocalTypography.current.caption, color = RefluxColors.contentSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button("Keep", onCancel, Modifier.initialFocus().takesFocus(), style = ButtonStyle.QUIET)
            Button(confirm, onConfirm, style = ButtonStyle.GLASS)
        }
    }
}

/** While shown, Back (a remote's, a controller's B, Escape) closes this panel before anything else on the page does. */
@Composable
internal fun ClosesOnBack(app: AppState, close: () -> Unit) {
    val current by rememberUpdatedState(close)
    DisposableEffect(app) {
        val closing: () -> Unit = { current() }
        app.openPanel(closing)
        onDispose { app.closedPanel(closing) }
    }
}

/**
 * Asking who is watching ([AppState.profileGate]): over everything, which it makes inert. Choosing a profile, or
 * entering the PIN of the one being opened.
 */
@Composable
internal fun ProfileGateOverlay(app: AppState, gate: ProfileGate) {
    val layout = LocalLayout.current
    Box(
        Modifier.fillMaxSize().background(RefluxColors.background).absorbsPointer().pane(if (gate is ProfileGate.Unlock) "Enter PIN" else "Who's watching"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = layout.safeMarginDp.dp + 8.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (gate) {
                is ProfileGate.Choose -> {
                    Text("Who's watching?", Modifier.heading(), style = LocalTypography.current.headline, align = TextAlign.Center)
                    app.profiles.forEachIndexed { index, profile ->
                        ProfileRow(profile, if (index == 0) Modifier.initialFocus() else Modifier) { app.chooseProfile(profile) }
                    }
                    if (!gate.required) Button("Cancel", { app.dispatch(dev.reflux.core.input.SemanticAction.BACK) }, style = ButtonStyle.QUIET)
                }
                is ProfileGate.Unlock -> when {
                    gate.resetting -> ResetProfile(app, gate)
                    gate.forgetting -> ForgotPin(app, gate)
                    else -> PinEntry(app, gate)
                }
            }
        }
    }
}

/** Asks for [profile]'s own PIN before it is deleted; a wrong one deletes nothing and counts as a try. */
@Composable
private fun DeletionPin(app: AppState, profile: Profile, onCancel: () -> Unit) {
    val refused = app.deletionRefused?.takeIf { it.profile == profile.id }
    var pin by remember(profile.id, refused?.attempt) { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().glass(MaterialRole.ELEVATED, Shapes.large).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Enter ${profile.name}'s PIN to delete it.", Modifier.heading(), style = LocalTypography.current.bodyStrong)
        TextInput(
            pin,
            { typed -> pin = typed.filter(Char::isDigit).take(PinPolicy.LENGTH); if (pin.length == PinPolicy.LENGTH) app.deleteProfile(profile, pin) },
            "PIN",
            Modifier.widthIn(max = 260.dp).initialFocus().takesFocus(),
            kind = InputKind.PIN,
            last = true,
            error = refused != null,
            onDone = { if (pin.length == PinPolicy.LENGTH) app.deleteProfile(profile, pin) },
        )
        refused?.problem?.let { Text(it, Modifier.announces(it), color = RefluxColors.contentSecondary) }
        Button("Cancel", onCancel, style = ButtonStyle.QUIET)
    }
}

@Composable
private fun PinEntry(app: AppState, gate: ProfileGate.Unlock) {
    var pin by remember(gate.profile.id, gate.attempt) { mutableStateOf("") }
    ProfileMark(gate.profile, 64.dp, lit = true)
    Text(gate.profile.name, Modifier.heading(), style = LocalTypography.current.headline, align = TextAlign.Center)
    Text("Enter this profile's PIN.", color = RefluxColors.contentSecondary, align = TextAlign.Center)
    TextInput(
        pin,
        { typed -> pin = typed.filter(Char::isDigit).take(PinPolicy.LENGTH); if (pin.length == PinPolicy.LENGTH) app.enterPin(pin) },
        "PIN",
        Modifier.widthIn(max = 260.dp).initialFocus(),
        kind = InputKind.PIN,
        last = true,
        error = gate.problem != null,
        onDone = { if (pin.length == PinPolicy.LENGTH) app.enterPin(pin) },
    )
    gate.problem?.let { Text(it, Modifier.announces(it), color = RefluxColors.contentSecondary, align = TextAlign.Center) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (app.profilesShown) Button("Someone else", { app.dispatch(dev.reflux.core.input.SemanticAction.BACK) }, style = ButtonStyle.QUIET)
        else if (!gate.required) Button("Cancel", { app.dispatch(dev.reflux.core.input.SemanticAction.BACK) }, style = ButtonStyle.QUIET)
    }
    Button("Forgot PIN", { app.forgotPin() }, style = ButtonStyle.QUIET)
}

/**
 * Forgot PIN (DECISIONS.md, 2026-11-19): the choices, with the safe one first. Keep profile changes nothing. Where the
 * device has a lock of its own, the system can confirm its owner and the PIN goes while everything stays (the old PIN is
 * never revealed, and Reflux never sees what is entered there). Reset profile is the destructive one, asked again
 * before anything goes ([ResetProfile]).
 */
@Composable
private fun ForgotPin(app: AppState, gate: ProfileGate.Unlock) {
    val name = gate.profile.name
    ProfileMark(gate.profile, 64.dp, lit = true)
    Text("Forgot $name's PIN?", Modifier.heading(), style = LocalTypography.current.headline, align = TextAlign.Center)
    Text("Reflux can't show or recover a PIN.", color = RefluxColors.contentSecondary, align = TextAlign.Center)
    val system = app.systemConfirmsOwner
    if (system) {
        Text(
            "Confirm it's you with this device's fingerprint, face, or screen lock to remove the PIN. $name's history, favorites, and sign-ins stay.",
            style = LocalTypography.current.caption,
            color = RefluxColors.contentTertiary,
            align = TextAlign.Center,
        )
    }
    gate.problem?.let { Text(it, Modifier.announces(it), color = RefluxColors.contentSecondary, align = TextAlign.Center) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button("Keep profile", { app.forgotPin(false) }, Modifier.initialFocus(), style = ButtonStyle.QUIET)
        if (system) Button("Use device lock", { app.removeForgottenPinWithSystem() }, style = ButtonStyle.GLASS, icon = Icon.LOCK)
        Button("Reset profile…", { app.confirmProfileReset() }, style = ButtonStyle.QUIET)
    }
}

/**
 * Starting a profile over when its PIN is forgotten: said plainly, with what goes and what stays, and the safe answer
 * first. The PIN protected what the profile kept, so that goes with it.
 */
@Composable
private fun ResetProfile(app: AppState, gate: ProfileGate.Unlock) {
    val name = gate.profile.name
    ProfileMark(gate.profile, 64.dp, lit = true)
    Text("Reset $name?", Modifier.heading(), style = LocalTypography.current.headline, align = TextAlign.Center)
    Text(
        "A forgotten PIN can't be recovered. Resetting removes the PIN and, because the PIN kept them private, everything $name kept on this device: " +
            "its watch history, favorites, collections, chosen versions, its downloads, and its sign-ins to servers.",
        color = RefluxColors.contentSecondary,
        align = TextAlign.Center,
    )
    Text(
        "Your media, your servers and their own watch history, shared folders, and other profiles are not changed. You can sign in to your servers again afterwards.",
        style = LocalTypography.current.caption,
        color = RefluxColors.contentTertiary,
        align = TextAlign.Center,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button("Keep profile", { app.forgotPin(false) }, Modifier.initialFocus(), style = ButtonStyle.QUIET)
        Button("Reset profile", { app.resetForgottenProfile() }, style = ButtonStyle.GLASS)
    }
}
