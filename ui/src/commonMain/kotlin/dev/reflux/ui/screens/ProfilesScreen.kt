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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
 * A profile's mark: its initial in a neutral circle (docs/DESIGN.md: the interface takes no color of its own). The
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
            Text(
                initial,
                style = if (size >= 56.dp) LocalTypography.current.title else LocalTypography.current.bodyStrong,
                color = if (lit) RefluxColors.background else RefluxColors.contentPrimary,
            )
        } else {
            Icon(Icon.PROFILE, size = size * 0.5f, tint = if (lit) RefluxColors.background else RefluxColors.contentPrimary)
        }
    }
}

/**
 * Who is watching (docs/NAVIGATION.md, section 1): the profile entry of the utility area. The profiles of this
 * installation, switching between them, adding one, and deleting one that is not open. On a phone, where the utility
 * area is this one entry, it also leads to Downloads, Sources, and Settings.
 */
@Composable
internal fun ProfilesScreen(app: AppState) {
    LaunchedEffect(Unit) { app.loadProfiles() }
    // A phone, upright or on its side: its utility area is this one entry.
    val phone = LocalLayout.current.navigation.let { it == dev.reflux.design.NavigationStyle.BOTTOM_BAR || it == dev.reflux.design.NavigationStyle.COMPACT_RAIL }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Profile?>(null) }
    Page {
        item(key = "heading") {
            Heading(
                if (app.profilesShown) "Who's watching" else "Profile",
                if (app.profilesShown) "Each profile has its own watch history, favorites, collections, and server accounts." else "Add a profile for each person who watches here. Each keeps their own history, favorites, and server accounts.",
            )
        }
        app.profiles.forEachIndexed { index, profile ->
            item(key = "profile/${profile.id.value}") {
                ProfileRow(profile, if (index == 0) Modifier.initialFocus() else Modifier) { app.chooseProfile(profile) }
            }
        }
        item(key = "add") {
            if (adding) {
                AddProfile(onAdd = { name -> adding = false; app.addProfile(name) }, onCancel = { adding = false })
            } else {
                Button("Add a profile", { adding = true }, Modifier.padding(top = 4.dp), icon = Icon.PLUS, style = ButtonStyle.QUIET)
            }
        }
        // Another viewer's profile can be deleted from here; the open one is changed in Settings.
        val others = app.profiles.filterNot { it.active }
        if (others.isNotEmpty()) {
            item(key = "delete") {
                val confirming = deleting
                if (confirming == null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        others.forEach { profile -> Button("Delete ${profile.name}…", { deleting = profile }, style = ButtonStyle.QUIET) }
                    }
                } else {
                    Confirm(
                        "Delete ${confirming.name}?",
                        "Its watch history, favorites, collections, and downloads go, and its server accounts are signed out of on this device. Your servers keep their own history.",
                        confirm = "Delete",
                        onConfirm = { deleting = null; app.deleteProfile(confirming) },
                        onCancel = { deleting = null },
                    )
                }
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

@Composable
private fun ProfileRow(profile: Profile, modifier: Modifier = Modifier, onClick: () -> Unit) {
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
        TextInput(name, { name = it.take(40) }, "Name", Modifier.initialFocus(), last = true, onDone = { if (name.isNotBlank()) onAdd(name) })
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button("Add", { onAdd(name) }, style = ButtonStyle.PRIMARY, enabled = name.isNotBlank())
            Button("Cancel", onCancel, style = ButtonStyle.QUIET)
        }
    }
}

/** A question that changes something that cannot be undone, said plainly, with the safe answer in reach first. */
@Composable
internal fun Confirm(title: String, body: String, confirm: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    Column(Modifier.fillMaxWidth().glass(MaterialRole.ELEVATED, Shapes.large).padding(16.dp).pane(title, onCancel), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, Modifier.heading(), style = LocalTypography.current.bodyStrong)
        Text(body, style = LocalTypography.current.caption, color = RefluxColors.contentSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button("Keep", onCancel, Modifier.initialFocus(), style = ButtonStyle.QUIET)
            Button(confirm, onConfirm, style = ButtonStyle.GLASS)
        }
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
                is ProfileGate.Unlock -> if (gate.forgetting) ForgotPin(app, gate) else PinEntry(app, gate)
            }
        }
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
 * Starting a profile over when its PIN is forgotten: said plainly, with what goes and what stays, and the safe answer
 * first. The PIN protected what the profile kept, so that goes with it.
 */
@Composable
private fun ForgotPin(app: AppState, gate: ProfileGate.Unlock) {
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
        Button("Keep it", { app.forgotPin(false) }, Modifier.initialFocus(), style = ButtonStyle.QUIET)
        Button("Reset $name", { app.resetForgottenProfile() }, style = ButtonStyle.GLASS)
    }
}
