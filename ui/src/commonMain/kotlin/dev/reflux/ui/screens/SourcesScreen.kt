package dev.reflux.ui.screens

import dev.reflux.ui.theme.States
import dev.reflux.ui.theme.Shapes
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import kotlinx.coroutines.withContext
import dev.reflux.design.FormFactor
import dev.reflux.design.MaterialRole
import dev.reflux.design.Motion
import dev.reflux.library.PipelineActivity
import dev.reflux.ui.LocalChromeInsets
import dev.reflux.ui.ReportPageScroll
import dev.reflux.ui.components.Button
import dev.reflux.ui.components.ButtonStyle
import dev.reflux.ui.components.Icon
import dev.reflux.ui.components.InputKind
import dev.reflux.ui.components.Pressable
import dev.reflux.ui.components.TextInput
import dev.reflux.ui.components.announces
import dev.reflux.ui.components.heading
import dev.reflux.ui.components.initialFocus
import dev.reflux.ui.components.rememberFocusReturn
import dev.reflux.ui.components.pane
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
import dev.reflux.ui.state.contents
import dev.reflux.ui.state.Formatting
import dev.reflux.ui.components.IconButton
import dev.reflux.library.SourceLibrary
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.toggleableState

/** The width a page of rows and forms keeps to, however wide the window: a column to read, not a dashboard. */
private val PageWidth = 640.dp

/** A page that is one calm column in the middle of the screen, starting below the navigation. */
@Composable
internal fun Page(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    val layout = LocalLayout.current
    val insets = LocalChromeInsets.current
    val list = rememberLazyListState()
    // Where pages move a row at a time (a television), what goes up under the navigation fades out.
    ReportPageScroll(remember { derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 0 } }.value)
    AmbientBackdrop()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            state = list,
            modifier = Modifier.widthIn(max = PageWidth + layout.safeMarginDp.dp * 2).fillMaxWidth(),
            contentPadding = PaddingValues(start = layout.safeMarginDp.dp, end = layout.safeMarginDp.dp, top = insets.top + 8.dp, bottom = insets.bottom + 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
internal fun Heading(title: String, body: String?) {
    Column(Modifier.padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, Modifier.heading(), style = LocalTypography.current.headline)
        body?.let { Text(it, color = RefluxColors.contentSecondary) }
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
    // Kept across a visit to a page it leads to (its libraries, signing in again), so coming back finds it open.
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    Page {
        item(key = "heading") { Heading("Sources", "Where your library comes from. Reflux only reads from these: your files are never renamed, moved, or changed. Open one to see everything on it, in your library or not.") }
        item(key = "actions") {
            FlowRow(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button("Add media", { app.navigator.open(Destination.AddSource) }, Modifier.initialFocus(), icon = Icon.PLUS, style = ButtonStyle.PRIMARY)
            }
        }
        if (model.rows.isEmpty()) {
            item(key = "empty") { Text("Nothing has been added yet. Add a folder, a server, or a network share, and your library builds itself.", color = RefluxColors.contentTertiary) }
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
                    color = RefluxColors.contentTertiary,
                )
            }
        }
        // The one appearance choice Reflux has: whether artwork may color the space around it. Off by default; it is
        // not a theme, and nothing else about the interface changes with it (DECISIONS.md, 2026-11-07).
        item(key = "atmosphere") {
            SwitchRow(
                "Media Atmosphere",
                "Let artwork tint the space around it, on Home and on a title's page.",
                app.mediaAtmosphere,
                Modifier.padding(top = 12.dp),
            ) { app.setMediaAtmosphere(!app.mediaAtmosphere) }
        }
        // Reflux itself, after everything about the library: a quiet way to its version, privacy, and credits.
        item(key = "about") {
            val back = rememberFocusReturn("sources/about")
            Option(Icon.INFO, "About Reflux", "Version ${app.platform.build.version}, privacy, licenses, and credits", back.modifier.padding(top = 12.dp)) {
                back.leaving()
                app.navigator.open(Destination.About)
            }
        }
    }
}

private fun iconOf(kind: ConnectionKind?): Icon = when (kind) {
    ConnectionKind.SERVER -> Icon.SERVER
    ConnectionKind.SHARE -> Icon.NETWORK
    null -> Icon.FOLDER
}

/**
 * A source as one quiet card: what it is, whether it is there, and whether it is in the library; selecting it
 * shows the rest: its switch and its libraries' (docs/NAVIGATION.md, section 5), the way into its own library, and
 * what can be done about it.
 */
@Composable
private fun SourceItem(app: AppState, row: SourceRow, expanded: Boolean, scanning: Boolean, onToggle: () -> Unit) {
    val shape = Shapes.large
    var removing by remember(row.id) { mutableStateOf(false) }
    // Down from an open card goes to its first control, never straight to Remove because it happens to be nearer.
    val firstAction = remember { FocusRequester() }
    // The button pressed goes when the question comes, and comes back when it goes, so focus has to be taken
    // across each way. With a remote it lands on Keep, so that a press too many keeps the source.
    val keep = remember { FocusRequester() }
    val asks = remember { FocusRequester() }
    // Down from the switches is the card's first action, Open, never whatever button happens to be nearer.
    val open = remember { FocusRequester() }
    val showsFocus = LocalLayout.current.showsFocus
    var asked by remember(row.id) { mutableStateOf(false) }
    LaunchedEffect(removing) {
        if (showsFocus && (removing || asked)) {
            withFrameNanos { }
            runCatching { (if (removing) keep else asks).requestFocus() }
        }
        asked = removing
    }
    Column(Modifier.fillMaxWidth().glass(MaterialRole.ELEVATED, shape)) {
        Pressable(
            onToggle,
            Modifier.fillMaxWidth().focusProperties { if (expanded) down = firstAction }.semantics { stateDescription = if (expanded) "Open" else "Closed" },
            shape,
            lift = 0f,
        ) { active ->
            Row(
                Modifier.fillMaxWidth().background(States.layer(if (active) 0.06f else 0f)).padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val away = !row.connected || row.signInRequired
                Box(Modifier.size(40.dp).background(States.layer(0.07f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(iconOf(row.kind), size = 20.dp, tint = if (away) RefluxColors.unavailable else RefluxColors.contentPrimary)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(row.name, style = LocalTypography.current.bodyStrong, maxLines = 1)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        val status = if (scanning) (if (row.connected) "Updating…" else "Connecting…") else row.statusLabel
                        if (away && !scanning) Box(Modifier.size(6.dp).background(RefluxColors.unavailable, CircleShape))
                        Text(
                            listOfNotNull(row.kindLabel, status, row.inclusionLabel).joinToString("  ·  "),
                            style = LocalTypography.current.caption,
                            color = if (away && !scanning) RefluxColors.contentSecondary else RefluxColors.contentTertiary,
                            maxLines = 1,
                        )
                    }
                }
                Icon(if (expanded) Icon.CHEVRON_UP else Icon.CHEVRON_DOWN, size = 16.dp, tint = RefluxColors.contentTertiary)
            }
        }
        AnimatedVisibility(expanded, enter = fadeIn(Motion.controlsShow.tween()) + expandVertically(Motion.controlsShow.tween()), exit = fadeOut(Motion.controlsHide.tween()) + shrinkVertically(Motion.controlsHide.tween())) {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val facts = listOfNotNull(row.address, row.account?.let { "Signed in as $it" }, row.updated)
                if (facts.isNotEmpty()) {
                    Column(Modifier.padding(start = 54.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        facts.forEach { Text(it, style = LocalTypography.current.caption, color = RefluxColors.contentTertiary, maxLines = 1) }
                    }
                }
                val note = when {
                    row.signInRequired -> "${row.name} no longer accepts Reflux's sign-in. Everything from it stays in your library; sign in again to play from it and keep it up to date."
                    !row.connected && row.kind == null -> "Its drive or folder isn't there right now. Everything from it stays in your library, and it comes back by itself when the folder does."
                    !row.connected -> "Reflux can't reach it right now. Everything from it stays in your library, and Reflux reconnects by itself when it's back."
                    else -> null
                }
                note?.let { Text(it, Modifier.padding(start = 54.dp), style = LocalTypography.current.caption, color = RefluxColors.contentSecondary) }
                if (!removing) {
                    // In the library or not: the source stays connected and can always be opened here.
                    SwitchRow(
                        "In your library",
                        if (row.included) "Its movies and shows are in Home, Movies, Shows, and Search." else "Its movies and shows are only here, under Sources.",
                        row.included,
                        Modifier.focusRequester(firstAction).then(if (row.libraries.isEmpty()) Modifier.focusProperties { down = open } else Modifier),
                    ) { app.setSourceIncluded(row.id, !row.included, row.name) }
                    if (row.libraries.isNotEmpty()) {
                        Text(
                            if (row.included) "Libraries" else "Libraries · they count again when ${row.name} is in your library",
                            Modifier.padding(start = 4.dp, top = 4.dp).heading(),
                            style = LocalTypography.current.captionStrong,
                            color = RefluxColors.contentSecondary,
                        )
                        row.libraries.forEach { library ->
                            LibraryRow(app, row, library, if (library == row.libraries.last()) open else null)
                        }
                    } else if (row.sectioned && row.connected) {
                        Text("No libraries with movies or shows yet.", Modifier.padding(start = 4.dp), style = LocalTypography.current.caption, color = RefluxColors.contentTertiary)
                    }
                }
                if (removing) {
                    Column(Modifier.pane("Remove ${row.name}?"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Remove ${row.name}?", style = LocalTypography.current.bodyStrong)
                        Text(
                            if (row.kind == ConnectionKind.SERVER) {
                                "Its movies and shows leave your library, and Reflux signs this device out of the server. What you have watched is remembered, and your media on it is not touched. To keep it connected but out of your library, switch off In your library instead."
                            } else {
                                "Its movies and shows leave your library. What you have watched is remembered, and nothing on it is touched. To keep it but out of your library, switch off In your library instead."
                            },
                            style = LocalTypography.current.caption,
                            color = RefluxColors.contentSecondary,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button("Remove", {
                                removing = false
                                app.removeSource(row.id)
                            }, Modifier.focusRequester(firstAction))
                            Button("Keep", { removing = false }, Modifier.focusRequester(keep), style = ButtonStyle.QUIET)
                        }
                    }
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val back = rememberFocusReturn("sources/open/${row.id.value}")
                        Button("Open", { back.leaving(); app.navigator.open(Destination.SourceLibrary(row.id)) }, back.modifier.focusRequester(open), icon = Icon.CHEVRON_RIGHT)
                        Button(if (row.connected && !row.signInRequired) "Refresh" else "Try again", { app.checkSource(row.id) }, icon = Icon.REFRESH, enabled = !scanning)
                        row.kind?.let { kind ->
                            Button(if (kind == ConnectionKind.SERVER) "Sign in again" else "Change", { app.navigator.open(Destination.Connect(kind, row.id)) })
                        }
                        Button("Remove", { removing = true }, Modifier.focusRequester(asks), style = ButtonStyle.QUIET)
                    }
                }
            }
        }
    }
}

/**
 * One library of a source: its own switch, kept as chosen; dimmed while its source is out of the library, where it
 * would not count; and the way into what it holds.
 */
@Composable
private fun LibraryRow(app: AppState, row: SourceRow, library: SourceLibrary, downTo: FocusRequester?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        val kind = when (library.kind) {
            SectionKind.MOVIES -> "Movies"
            SectionKind.SHOWS -> "Shows"
            SectionKind.MIXED -> "Movies and shows"
        }
        SwitchRow(
            library.name,
            listOfNotNull(kind, Formatting.contents(library.contents.movies, library.contents.shows)).joinToString("  ·  "),
            library.included,
            Modifier.weight(1f).then(if (downTo != null) Modifier.focusProperties { down = downTo } else Modifier),
            dimmed = !row.included,
        ) { app.setLibraryIncluded(row.id, library.id, !library.included, library.name) }
        val back = rememberFocusReturn("sources/library/${row.id.value}/${library.id}")
        IconButton(Icon.CHEVRON_RIGHT, "Open ${library.name}", { back.leaving(); app.navigator.open(Destination.SourceLibrary(row.id, library.id)) }, back.modifier, tint = RefluxColors.contentSecondary)
    }
}

/**
 * A switch with its name and what it means: a whole row to press, with the role and state of a switch for assistive
 * technology. On is a filled white mark; off, an empty ring. No color is needed to tell them apart.
 */
@Composable
private fun SwitchRow(title: String, body: String?, on: Boolean, modifier: Modifier = Modifier, dimmed: Boolean = false, onToggle: () -> Unit) {
    val shape = Shapes.medium
    Pressable(onToggle, modifier.fillMaxWidth().semantics { toggleableState = ToggleableState(on) }, shape, lift = 0f, role = Role.Switch) { active ->
        Row(
            Modifier.fillMaxWidth().background(States.layer(if (active) 0.07f else 0.03f)).padding(horizontal = 14.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = LocalTypography.current.bodyStrong, color = if (on && !dimmed) RefluxColors.contentPrimary else RefluxColors.contentSecondary, maxLines = 1)
                body?.let { Text(it, style = LocalTypography.current.caption, color = RefluxColors.contentTertiary, maxLines = 2) }
            }
            val mark = if (dimmed) 0.45f else 1f
            Box(
                Modifier.size(44.dp, 26.dp).background(States.layer(if (on) 0.9f * mark else 0f), CircleShape)
                    .border(1.5.dp, States.layer(if (on) 0f else 0.35f * mark), CircleShape).padding(3.dp),
                contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Box(Modifier.size(20.dp).background(if (on) RefluxColors.background else States.layer(0.55f * mark), CircleShape))
            }
        }
    }
}

/**
 * What kind of media to add. A folder is first and needs nothing more; a server or a share leads to a
 * short form. Only what this platform can connect to is offered, each server by its own name.
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
            val products = connections?.products(ConnectionKind.SERVER).orEmpty()
            if (products.isEmpty()) {
                item(key = "server") {
                    Option(Icon.SERVER, "Media server", "Sign in to your media server. What you watch stays in step with it.") {
                        app.navigator.open(Destination.Connect(ConnectionKind.SERVER))
                    }
                }
            }
            products.forEach { connector ->
                item(key = "server/${connector.type}") {
                    Option(Icon.SERVER, connector.product ?: "Media server", "Sign in to your ${connector.product} server. What you watch stays in step with it.") {
                        app.navigator.open(Destination.Connect(ConnectionKind.SERVER, type = connector.type))
                    }
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
    val shape = Shapes.large
    Pressable(onClick, modifier.fillMaxWidth(), shape, lift = 0.15f) { active ->
        Row(
            Modifier.fillMaxWidth().glass(MaterialRole.ELEVATED, shape).background(States.layer(if (active) 0.07f else 0f))
                .padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(44.dp).background(States.layer(0.07f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, size = 22.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = LocalTypography.current.bodyStrong, maxLines = 1)
                Text(body, style = LocalTypography.current.caption, color = RefluxColors.contentSecondary, maxLines = 2)
            }
            Icon(Icon.CHEVRON_RIGHT, size = 16.dp, tint = RefluxColors.contentTertiary)
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
    val setup = remember(destination) { SourceSetup(app, connections, destination.kind, record, destination.type ?: record?.type) }
    val plex = setup.product == "Plex"
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
    val body = if (plex) {
        "Sign in with your Plex account, or put a Plex token in the password field. Leave both empty if your server lets this network in without signing in. Reflux keeps only Plex's permission to read; your password isn't stored."
    } else if (server) {
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
                    Text("For example ${setup.examples.joinToString(" or ")}", style = LocalTypography.current.caption, color = RefluxColors.contentTertiary)
                }
                TextInput(
                    setup.username, { setup.username = it }, if (plex) "Plex account email or user name" else if (setup.anonymous) "User name (if it asks)" else "User name",
                    kind = InputKind.NAME, error = setup.problemField == SourceSetup.Field.USERNAME, enabled = !setup.connecting,
                    onFocus = typing, onDone = { focus.moveFocus(FocusDirection.Down) },
                )
                TextInput(
                    setup.password, { setup.password = it }, if (plex) "Password or Plex token" else if (setup.anonymous) "Password (if it asks)" else "Password",
                    Modifier.focusRequester(password),
                    // Down from the last field is the way to connect, not whatever button is nearest.
                    fieldModifier = Modifier.focusProperties { down = submit },
                    kind = InputKind.SECRET, last = true, error = setup.problemField == SourceSetup.Field.PASSWORD, enabled = !setup.connecting,
                    onFocus = typing, onDone = { setup.connect() },
                )
            }
        }
        item(key = "problem") {
            // What went wrong is said when it happens, not only where someone is looking.
            Box(Modifier.fillMaxWidth().announces(setup.problem)) {
                AnimatedVisibility(setup.problem != null, enter = fadeIn(Motion.controlsShow.tween()), exit = fadeOut(Motion.controlsHide.tween())) {
                    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                        Box(Modifier.padding(top = 6.dp).size(6.dp).background(RefluxColors.error, CircleShape))
                        Text(setup.problem.orEmpty(), color = RefluxColors.contentPrimary)
                    }
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
