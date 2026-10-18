package dev.reflux.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import dev.reflux.core.model.SourceId
import dev.reflux.core.source.ConnectionKind
import dev.reflux.core.source.SectionKind
import dev.reflux.core.source.SourceSection
import dev.reflux.library.SectionChoice
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import kotlinx.coroutines.withContext
import dev.reflux.design.FormFactor
import dev.reflux.design.MaterialRole
import dev.reflux.design.Motion
import dev.reflux.library.PipelineActivity
import dev.reflux.library.downloads
import dev.reflux.ui.LocalChromeInsets
import dev.reflux.ui.components.Button
import dev.reflux.ui.components.ButtonStyle
import dev.reflux.ui.components.Icon
import dev.reflux.ui.components.InputKind
import dev.reflux.ui.components.Pressable
import dev.reflux.ui.components.TextInput
import dev.reflux.ui.components.initialFocus
import dev.reflux.ui.state.AppState
import dev.reflux.ui.state.Destination
import dev.reflux.ui.state.SourceRow
import dev.reflux.ui.state.SourceSetup
import dev.reflux.ui.state.libraryState
import dev.reflux.ui.state.sourcesModel
import dev.reflux.ui.theme.LocalLayout
import dev.reflux.ui.theme.LocalTypography
import dev.reflux.ui.theme.RefluxColors
import dev.reflux.ui.theme.Text
import dev.reflux.ui.theme.glass
import dev.reflux.ui.theme.tween

/** The width a page of rows and forms keeps to, however wide the window: a column to read, not a dashboard. */
private val PageWidth = 640.dp

/** A page that is one calm column in the middle of the screen, starting below the navigation. */
@Composable
internal fun Page(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    val layout = LocalLayout.current
    val insets = LocalChromeInsets.current
    AmbientBackdrop()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = PageWidth + layout.safeMarginDp.dp * 2).fillMaxWidth(),
            contentPadding = PaddingValues(start = layout.safeMarginDp.dp, end = layout.safeMarginDp.dp, top = insets.top + 8.dp, bottom = insets.bottom + 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
internal fun Heading(title: String, body: String?) {
    Column(Modifier.padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = LocalTypography.current.headline)
        body?.let { Text(it, color = RefluxColors.textSecondary) }
    }
}

/**
 * Where the library comes from: every source with whether it is there, and, one step further in, what it
 * is, where, and what can be done about it. A source that is not connected stays here and in the library.
 */
@Composable
internal fun SourcesScreen(app: AppState) {
    val connections = app.platform.connections
    val loaded by app.libraryState { sourcesModel(connections) }
    val model = loaded ?: return
    val activity by app.activity.collectAsState()
    val metadataUnreachable by app.metadataUnreachable.collectAsState()
    val keeps by app.libraryState { app.platform.offline?.let { it.possible() || downloads().isNotEmpty() } ?: false }
    // Kept across a visit to a page it leads to (its libraries, signing in again), so coming back finds it open.
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    Page {
        item(key = "heading") { Heading("Media sources", "Reflux only reads from these. Your files are never renamed, moved, or changed.") }
        item(key = "actions") {
            FlowRow(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button("Add media", { app.navigator.open(Destination.AddSource) }, Modifier.initialFocus(), icon = Icon.PLUS, style = ButtonStyle.PRIMARY)
                if (keeps == true) Button("Downloads", { app.navigator.open(Destination.Downloads) }, icon = Icon.DOWNLOAD)
            }
        }
        if (model.rows.isEmpty()) {
            item(key = "empty") { Text("Nothing has been added yet. Add a folder, a server, or a network share, and your library builds itself.", color = RefluxColors.textTertiary) }
        }
        items(model.rows, key = { it.id.value }) { row ->
            SourceItem(
                app,
                row,
                expanded = open == row.id.value,
                scanning = row.id in app.checking || (activity as? PipelineActivity.Scanning)?.sourceName == row.name,
                onToggle = { open = if (open == row.id.value) null else row.id.value },
            )
        }
        if (metadataUnreachable) {
            item(key = "metadata") {
                Text(
                    "Titles and artwork couldn't be fetched just now. Your library works without them, and Reflux will try again.",
                    Modifier.padding(top = 6.dp),
                    style = LocalTypography.current.caption,
                    color = RefluxColors.textTertiary,
                )
            }
        }
    }
}

private fun iconOf(kind: ConnectionKind?): Icon = when (kind) {
    ConnectionKind.SERVER -> Icon.SERVER
    ConnectionKind.SHARE -> Icon.NETWORK
    null -> Icon.FOLDER
}

/** A source as one quiet card: what it is and whether it is there; selecting it shows the rest. */
@Composable
private fun SourceItem(app: AppState, row: SourceRow, expanded: Boolean, scanning: Boolean, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    var removing by remember(row.id) { mutableStateOf(false) }
    // Down from an open card goes to its first action, never straight to Remove because it happens to be nearer.
    val firstAction = remember { FocusRequester() }
    Column(Modifier.fillMaxWidth().glass(MaterialRole.ELEVATED, shape)) {
        Pressable(onToggle, Modifier.fillMaxWidth().focusProperties { if (expanded) down = firstAction }, shape, lift = 0f) { active ->
            Row(
                Modifier.fillMaxWidth().background(Color.White.copy(alpha = if (active) 0.06f else 0f)).padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(40.dp).background(Color.White.copy(alpha = 0.07f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(iconOf(row.kind), size = 20.dp, tint = if (row.connected) RefluxColors.textPrimary else RefluxColors.unavailable)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(row.name, style = LocalTypography.current.bodyStrong, maxLines = 1)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        val status = when {
                            scanning -> if (row.connected) "Updating…" else "Connecting…"
                            !row.connected -> "Not connected"
                            else -> row.contents ?: if (row.kind == null) "On this device" else "Connected"
                        }
                        if (!row.connected && !scanning) Box(Modifier.size(6.dp).background(RefluxColors.unavailable, CircleShape))
                        Text(
                            "${row.kindLabel}  ·  $status",
                            style = LocalTypography.current.caption,
                            color = if (!row.connected && !scanning) RefluxColors.textSecondary else RefluxColors.textTertiary,
                            maxLines = 1,
                        )
                    }
                }
                Icon(if (expanded) Icon.CHEVRON_UP else Icon.CHEVRON_DOWN, size = 16.dp, tint = RefluxColors.textTertiary)
            }
        }
        AnimatedVisibility(expanded, enter = fadeIn(Motion.controlsShow.tween()) + expandVertically(Motion.controlsShow.tween()), exit = fadeOut(Motion.controlsHide.tween()) + shrinkVertically(Motion.controlsHide.tween())) {
            Column(Modifier.fillMaxWidth().padding(start = 70.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val facts = listOfNotNull(
                    row.address,
                    row.account?.let { "Signed in as $it" },
                    when {
                        !row.sectioned -> null
                        row.excluded == 0 -> "All libraries"
                        row.excluded == 1 -> "1 library left out"
                        else -> "${row.excluded} libraries left out"
                    },
                    row.updated,
                )
                if (facts.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        facts.forEach { Text(it, style = LocalTypography.current.caption, color = RefluxColors.textTertiary, maxLines = 1) }
                    }
                }
                if (!row.connected) {
                    Text(
                        if (row.kind == null) {
                            "Its drive or folder isn't there right now. Everything from it stays in your library, and it comes back by itself when the folder does."
                        } else {
                            "Reflux can't reach it right now. Everything from it stays in your library, and Reflux reconnects by itself when it's back. If its password changed, sign in again."
                        },
                        style = LocalTypography.current.caption,
                        color = RefluxColors.textSecondary,
                    )
                }
                if (removing) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Remove ${row.name}?", style = LocalTypography.current.bodyStrong)
                        Text(
                            "Its movies and shows leave your library. What you have watched is remembered, and nothing on it is touched.",
                            style = LocalTypography.current.caption,
                            color = RefluxColors.textSecondary,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button("Remove", {
                                removing = false
                                app.removeSource(row.id)
                            }, Modifier.focusRequester(firstAction))
                            Button("Keep", { removing = false }, style = ButtonStyle.QUIET)
                        }
                    }
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(if (row.connected) "Refresh" else "Try again", { app.checkSource(row.id) }, Modifier.focusRequester(firstAction), icon = Icon.REFRESH, enabled = !scanning)
                        if (row.sectioned) Button("Libraries", { app.navigator.open(Destination.SourceSections(row.id)) })
                        row.kind?.let { kind ->
                            Button(if (kind == ConnectionKind.SERVER) "Sign in again" else "Change", { app.navigator.open(Destination.Connect(kind, row.id)) })
                        }
                        Button("Remove", { removing = true }, style = ButtonStyle.QUIET)
                    }
                }
            }
        }
    }
}

/**
 * What kind of media to add. A folder is first and needs nothing more; a server or a share leads to a
 * short form. Only what this platform can connect to is offered.
 */
@Composable
internal fun AddSourceScreen(app: AppState) {
    val connections = app.platform.connections
    val kinds = connections?.kinds.orEmpty()
    Page {
        item(key = "heading") { Heading("Add media", "Reflux finds the movies and shows in it, and builds your library. Nothing is moved or changed.") }
        item(key = "folder") {
            Option(Icon.FOLDER, "A folder on this device", "Movies and shows stored here, or on a drive connected to it.", Modifier.initialFocus()) { app.addFolder() }
        }
        if (ConnectionKind.SERVER in kinds) {
            val product = connections?.connectors?.firstOrNull { it.kind == ConnectionKind.SERVER }?.product
            item(key = "server") {
                Option(Icon.SERVER, product ?: "Media server", "Sign in to your media server. What you watch stays in step with it.") {
                    app.navigator.open(Destination.Connect(ConnectionKind.SERVER))
                }
            }
        }
        if (ConnectionKind.SHARE in kinds) {
            item(key = "share") {
                Option(Icon.NETWORK, "Network share", "A folder shared by a computer, a NAS, or web storage.") {
                    app.navigator.open(Destination.Connect(ConnectionKind.SHARE))
                }
            }
        }
    }
}

@Composable
private fun Option(icon: Icon, title: String, body: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Pressable(onClick, modifier.fillMaxWidth(), shape, lift = 0.15f) { active ->
        Row(
            Modifier.fillMaxWidth().glass(MaterialRole.ELEVATED, shape).background(Color.White.copy(alpha = if (active) 0.07f else 0f))
                .padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp).background(Color.White.copy(alpha = 0.07f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, size = 22.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = LocalTypography.current.bodyStrong, maxLines = 1)
                Text(body, style = LocalTypography.current.caption, color = RefluxColors.textSecondary, maxLines = 2)
            }
            Icon(Icon.CHEVRON_RIGHT, size = 16.dp, tint = RefluxColors.textTertiary)
        }
    }
}

/**
 * Connecting a server or a share: its address, and a name and password where it needs them. The keyboard's
 * own key goes from one field to the next and connects from the last; nothing is asked that Reflux can
 * work out from the address.
 */
@Composable
internal fun ConnectScreen(app: AppState, destination: Destination.Connect) {
    val connections = app.platform.connections ?: return
    val record by app.libraryState(destination.editing) { destination.editing?.let { source(it) } }
    if (destination.editing != null && record == null) return
    val setup = remember(destination) { SourceSetup(app, connections, destination.kind, record) }
    val server = setup.kind == ConnectionKind.SERVER
    val focus = LocalFocusManager.current
    val address = remember { FocusRequester() }
    val password = remember { FocusRequester() }
    val submit = remember { FocusRequester() }
    val television = LocalLayout.current.formFactor == FormFactor.TV
    // A keyboard or a touch screen can type at once; a remote arrives on the first field and opens the
    // television's keyboard when the user chooses to.
    LaunchedEffect(Unit) {
        if (!television) runCatching { if (setup.editing != null && server) password.requestFocus() else address.requestFocus() }
    }
    DisposableEffect(Unit) { onDispose { app.textInputActive = false } }
    val title = when {
        setup.editing != null && server -> "Sign in to ${setup.editing.displayName} again"
        setup.editing != null -> "Change ${setup.editing.displayName}"
        server -> "Connect to ${setup.product ?: "your server"}"
        else -> "Add a network share"
    }
    val body = if (server) {
        "Reflux signs in once and keeps only the server's permission to read. Your password isn't stored."
    } else {
        "Reflux only reads from the share. Leave the name and password empty if it doesn't ask for them."
    }
    Page {
        item(key = "heading") { Heading(title, body) }
        item(key = "form") {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val typing: (Boolean) -> Unit = { if (it) app.textInputActive = true else app.textInputActive = false }
                TextInput(
                    setup.address, { setup.address = it }, if (server) "Server address" else "Share address",
                    Modifier.focusRequester(address).initialFocus(),
                    kind = InputKind.ADDRESS, placeholder = setup.examples.firstOrNull(),
                    error = setup.problemField == SourceSetup.Field.ADDRESS, enabled = !setup.connecting,
                    onFocus = typing, onDone = { focus.moveFocus(FocusDirection.Down) },
                )
                if (!server && setup.examples.size > 1) {
                    Text("For example ${setup.examples.joinToString(" or ")}", style = LocalTypography.current.caption, color = RefluxColors.textTertiary)
                }
                TextInput(
                    setup.username, { setup.username = it }, if (setup.anonymous) "User name (if it asks)" else "User name",
                    kind = InputKind.NAME, error = setup.problemField == SourceSetup.Field.USERNAME, enabled = !setup.connecting,
                    onFocus = typing, onDone = { focus.moveFocus(FocusDirection.Down) },
                )
                TextInput(
                    setup.password, { setup.password = it }, if (setup.anonymous) "Password (if it asks)" else "Password",
                    Modifier.focusRequester(password),
                    // Down from the last field is the way to connect, not whatever button is nearest.
                    fieldModifier = Modifier.focusProperties { down = submit },
                    kind = InputKind.SECRET, last = true, error = setup.problemField == SourceSetup.Field.PASSWORD, enabled = !setup.connecting,
                    onFocus = typing, onDone = { setup.connect() },
                )
            }
        }
        item(key = "problem") {
            AnimatedVisibility(setup.problem != null, enter = fadeIn(Motion.controlsShow.tween()), exit = fadeOut(Motion.controlsHide.tween())) {
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 6.dp).size(6.dp).background(RefluxColors.error, CircleShape))
                    Text(setup.problem.orEmpty(), color = RefluxColors.textPrimary)
                }
            }
        }
        item(key = "actions") {
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(if (setup.connecting) "Connecting…" else if (setup.editing != null && server) "Sign in" else "Connect", { setup.connect() }, Modifier.focusRequester(submit), style = ButtonStyle.PRIMARY, enabled = !setup.connecting)
                Button("Cancel", { app.navigator.back() }, style = ButtonStyle.QUIET)
            }
        }
    }
}

/**
 * Which of a server's libraries Reflux includes. Everything is included until the user says otherwise;
 * what is left out stays on the server and leaves Reflux's library, and at least one library stays in.
 */
@Composable
internal fun SourceSectionsScreen(app: AppState, sourceId: SourceId) {
    val connections = app.platform.connections ?: return
    val record by app.libraryState(sourceId) { source(sourceId) }
    val name = record?.displayName ?: return
    var choice by remember(sourceId) { mutableStateOf<SectionChoice?>(null) }
    var unreachable by remember(sourceId) { mutableStateOf(false) }
    var included by remember(sourceId) { mutableStateOf<Set<String>>(emptySet()) }
    val save = remember { FocusRequester() }
    LaunchedEffect(sourceId) {
        val loaded = runCatching { withContext(app.background) { connections.sections(sourceId) } }.getOrNull()
        if (loaded == null) {
            unreachable = true
        } else {
            choice = loaded
            included = loaded.sections.map { it.id }.toSet() - loaded.excluded
        }
    }
    Page {
        item(key = "heading") { Heading("Libraries on $name", "Choose what Reflux includes. What you leave out stays on the server, and you can add it back at any time.") }
        val loaded = choice
        when {
            unreachable -> item(key = "away") {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("$name isn't connected, so its libraries can't be listed right now. Try again when it's back.", color = RefluxColors.textSecondary)
                    Button("Back", { app.navigator.back() }, Modifier.initialFocus(), icon = Icon.BACK, style = ButtonStyle.PRIMARY)
                }
            }
            loaded == null -> item(key = "loading") { Text("Asking $name for its libraries…", color = RefluxColors.textTertiary) }
            else -> {
                items(loaded.sections, key = { it.id }) { section ->
                    val on = section.id in included
                    val last = on && included.size == 1
                    SectionRow(
                        section,
                        on,
                        when (section) {
                            loaded.sections.first() -> Modifier.initialFocus()
                            // Down from the last library is the way to Save, not whichever button is nearer.
                            loaded.sections.last() -> Modifier.focusProperties { down = save }
                            else -> Modifier
                        },
                    ) { if (!last) included = if (on) included - section.id else included + section.id }
                }
                item(key = "note") {
                    if (included.size == 1) {
                        Text(
                            "One library always stays included. To include nothing from $name, remove it from your sources instead.",
                            Modifier.padding(top = 4.dp),
                            style = LocalTypography.current.caption,
                            color = RefluxColors.textTertiary,
                        )
                    }
                }
                item(key = "actions") {
                    val excluded = loaded.sections.map { it.id }.toSet() - included
                    val changed = excluded != loaded.excludedPresent
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button("Save", { app.chooseSections(sourceId, excluded, loaded.sections, name) }, Modifier.focusRequester(save), style = ButtonStyle.PRIMARY, enabled = changed && included.isNotEmpty())
                        Button("Cancel", { app.navigator.back() }, style = ButtonStyle.QUIET)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionRow(section: SourceSection, on: Boolean, modifier: Modifier, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Pressable(onToggle, modifier.fillMaxWidth().semantics { stateDescription = if (on) "Included" else "Left out" }, shape, lift = 0f, role = Role.Checkbox) { active ->
        Row(
            Modifier.fillMaxWidth().glass(MaterialRole.ELEVATED, shape).background(Color.White.copy(alpha = if (active) 0.07f else 0f))
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(section.name, style = LocalTypography.current.bodyStrong, color = if (on) RefluxColors.textPrimary else RefluxColors.textSecondary, maxLines = 1)
                Text(
                    when (section.kind) {
                        SectionKind.MOVIES -> "Movies"
                        SectionKind.SHOWS -> "Shows"
                        SectionKind.MIXED -> "Movies and shows"
                    } + if (on) "" else "  ·  Left out",
                    style = LocalTypography.current.caption,
                    color = RefluxColors.textTertiary,
                    maxLines = 1,
                )
            }
            // Included is a filled white mark; left out, an empty ring. No color is needed to tell them apart.
            Box(
                Modifier.size(26.dp).background(if (on) Color.White.copy(alpha = 0.92f) else Color.Transparent, CircleShape)
                    .border(1.5.dp, Color.White.copy(alpha = if (on) 0f else 0.35f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (on) Icon(Icon.CHECK, size = 16.dp, tint = RefluxColors.background)
            }
        }
    }
}
