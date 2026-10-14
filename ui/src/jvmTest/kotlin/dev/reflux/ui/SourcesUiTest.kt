package dev.reflux.ui

import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import dev.reflux.core.identify.ParsedKind
import dev.reflux.core.identify.ParsedMedia
import dev.reflux.core.input.Key
import dev.reflux.core.model.Availability
import dev.reflux.core.model.StableIds
import dev.reflux.core.playback.AudioCodec
import dev.reflux.core.playback.AudioStream
import dev.reflux.core.playback.Container
import dev.reflux.core.playback.StreamInfo
import dev.reflux.core.playback.VideoCodec
import dev.reflux.core.playback.VideoStream
import dev.reflux.core.source.CatalogEntry
import dev.reflux.core.source.CatalogSource
import dev.reflux.core.source.CatalogVersion
import dev.reflux.core.source.Connection
import dev.reflux.core.source.ConnectionKind
import dev.reflux.core.source.ConnectionProblem
import dev.reflux.core.source.ConnectionRequest
import dev.reflux.core.source.PlaybackTarget
import dev.reflux.core.source.SourceCapability
import dev.reflux.core.source.SourceConnector
import dev.reflux.core.source.SourceDescriptor
import dev.reflux.core.source.SourceLocality
import dev.reflux.core.source.SourceUnavailableException
import dev.reflux.library.SourceConnections
import dev.reflux.ui.state.Destination
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val MINUTE = 60_000L

/** A media server on the home network, with two films of its own, that lets in alex with "secret". */
private class HomeServer(val address: String) : CatalogSource {
    override val descriptor = SourceDescriptor(StableIds.sourceId("home", address), "home", "Living Room", SourceLocality.LOCAL_NETWORK, setOf(SourceCapability.PROVIDES_CATALOG))
    override suspend fun availability() = if (HomeNetwork.serverUp) Availability.AVAILABLE else Availability.UNAVAILABLE
    override suspend fun playbackTarget(path: String) = PlaybackTarget("http://home.local/$path")

    private fun film(title: String, year: Int) = CatalogEntry(
        ParsedMedia(ParsedKind.MOVIE, title, year),
        listOf(CatalogVersion("items/${title.lowercase()}", 3_000_000_000, 1, StreamInfo(Container.MATROSKA, VideoStream(VideoCodec.H264, 1920, 1080, 8), listOf(AudioStream(AudioCodec.AAC, 2, "eng")), durationMs = 120 * MINUTE))),
    )

    override fun catalog(): Flow<CatalogEntry> =
        if (HomeNetwork.serverUp) listOf(film("Ronin", 1998), film("Sicario", 2015)).asFlow() else flow { throw SourceUnavailableException(descriptor.id) }
}

/** The network the tests' sources live on: a server that can be switched off, and a share that is never there. */
private object HomeNetwork {
    @Volatile var serverUp = true

    val server = object : SourceConnector {
        override val type = "home"
        override val kind = ConnectionKind.SERVER
        override val product = "Jellyfin"
        override val addressExample = "http://192.168.1.10:8096"
        override val anonymous = false
        override fun accepts(address: String) = address.isNotBlank()
        override suspend fun connect(request: ConnectionRequest): Connection = when {
            !request.address.startsWith("http://home.local") -> Connection.Failed(ConnectionProblem.UNREACHABLE)
            !serverUp -> Connection.Failed(ConnectionProblem.UNREACHABLE)
            request.password != "secret" -> Connection.Failed(ConnectionProblem.AUTHENTICATION, "HTTP 401")
            else -> Connection.Established(HomeServer(request.address), "address=${request.address}\nuser=${request.username}\ntoken=t0k")
        }
        override fun describe(config: String) = config.lines().associate { it.substringBefore('=') to it.substringAfter('=') }.let { ConnectionRequest(it.getValue("address"), it["user"]) }
    }

    val share = object : SourceConnector {
        override val type = "share"
        override val kind = ConnectionKind.SHARE
        override val product: String? = null
        override val addressExample = "\\\\nas\\Movies"
        override val anonymous = true
        override fun accepts(address: String) = address.startsWith("\\\\")
        override suspend fun connect(request: ConnectionRequest): Connection = Connection.Failed(ConnectionProblem.NOT_FOUND)
        override fun describe(config: String): ConnectionRequest? = null
    }
}

/**
 * Adding and looking after sources, as the user meets it: a folder first and fastest, a server or a share
 * one step aside, and a source that is away still in the library and still on the list.
 */
@OptIn(ExperimentalTestApi::class)
class SourcesUiTest {
    private fun DesktopComposeUiTest.shows(text: String) = onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun world(empty: Boolean = false): Pair<TestWorld, FakePlatform> {
        HomeNetwork.serverUp = true
        val world = if (empty) TestWorld().also { it.library.removeSource(it.source.descriptor.id); it.registry.unregister(it.source.descriptor.id) } else populated()
        val connections = SourceConnections(world.library, world.registry, listOf(HomeNetwork.server, HomeNetwork.share))
        return world to FakePlatform(connections = connections)
    }

    private fun DesktopComposeUiTest.signIn(password: String) {
        onNodeWithContentDescription("Server address").performTextReplacement("http://home.local:8096")
        onNodeWithContentDescription("User name").performTextReplacement("alex")
        onNodeWithContentDescription("Password").performTextReplacement(password)
        onNodeWithText("Connect").performClick()
    }

    @Test
    fun aFolderIsTheFirstWayInAndAServerIsOneStepAside() {
        val (world, platform) = world(empty = true)
        screen(world, platform) { app ->
            awaitText("Point Reflux at your media")
            assertTrue(shows("Choose a folder"), "the folder is still the main action")
            save("first-run")
            onNodeWithText("Connect a server or share").performClick()
            awaitText("Add media")
            assertEquals(Destination.AddSource, app.navigator.current)
            save("add-media")
            onNodeWithText("Jellyfin").performClick()
            awaitText("Connect to Jellyfin")

            // Nothing typed yet: said before anything is contacted.
            onNodeWithText("Connect").performClick()
            awaitText("Enter the address first.")

            signIn("guess")
            awaitText("That user name or password wasn't accepted.")
            save("connect-error")
            assertTrue(world.library.sources().isEmpty(), "a failed sign-in adds nothing")

            signIn("secret")
            // The flow is left, the server is a source, and its films arrive by themselves.
            await("the flow to be left") { app.navigator.current == Destination.Home }
            await("the notice") { shows("Living Room is connected") }
            await("the server's films") { world.library.movies().any { it.item.title == "Ronin" } }
            awaitText("Ronin")
            val record = world.library.sources().single()
            assertFalse("secret" in record.config, "the password is not kept")
            save("first-run-connected")
        }
    }

    @Test
    fun aShareThatIsNotThereIsSaidSoInWords() {
        val (world, platform) = world()
        screen(world, platform) { app ->
            awaitText("Recently Added")
            runOnIdle { app.navigator.open(Destination.Connect(ConnectionKind.SHARE)) }
            awaitText("Add a network share")
            onNodeWithContentDescription("Share address").performTextInput("ftp://nas/movies")
            onNodeWithText("Connect").performClick()
            await("the problem") { shows("That isn't a share address Reflux can open here.") }
            onNodeWithContentDescription("Share address").performTextReplacement("\\\\nas\\Movies")
            onNodeWithText("Connect").performClick()
            await("the problem") { shows("couldn't find that shared folder") }
            save("connect-share-error")
            // Back leaves the form without adding anything.
            runOnIdle { app.dispatch(dev.reflux.core.input.SemanticAction.BACK) }
            await("the form to be left") { app.navigator.current == Destination.Home }
            assertEquals(1, world.library.sources().size)
        }
    }

    @Test
    fun aServerThatIsAwayStaysListedAndInTheLibraryAndCanBeSignedInAgain() {
        val (world, platform) = world()
        screen(world, platform) { app ->
            awaitText("Recently Added")
            runOnIdle { app.navigator.open(Destination.Connect(ConnectionKind.SERVER)) }
            awaitText("Connect to Jellyfin")
            signIn("secret")
            await("the server's films") { world.library.movies().any { it.item.title == "Ronin" } }

            onNodeWithContentDescription("Media sources").performClick()
            awaitText("Living Room")
            await("what is on it") { shows("1 movie") || shows("2 movies") }
            save("sources")

            // The server goes away: nothing is removed, and the page says so plainly.
            HomeNetwork.serverUp = false
            runOnIdle { app.refresh() }
            await("the server to be seen as away") { shows("Not connected") }
            assertTrue(world.library.movies().any { it.item.title == "Ronin" }, "its films stay in the library")
            onNodeWithText("Living Room").performClick()
            awaitText("Try again")
            assertTrue(shows("reconnects by itself"))
            assertTrue(shows("http://home.local:8096") && shows("Signed in as alex"))
            save("sources-disconnected")

            // Trying again while it is still away changes nothing; once it is back, it is connected again.
            onNodeWithText("Try again").performClick()
            await("the check to finish") { app.checking.isEmpty() }
            assertTrue(shows("Not connected"))
            HomeNetwork.serverUp = true
            onNodeWithText("Try again").performClick()
            await("the server to be back") { !shows("Not connected") }

            // Signing in again keeps the source, its name, and its library.
            val id = world.library.sources().single { it.type == "home" }.id
            onNodeWithText("Sign in again").performClick()
            awaitText("Sign in to Living Room again")
            assertEquals("http://home.local:8096", app.navigator.current.let { (it as Destination.Connect).editing }?.let { world.library.source(it)?.config?.lines()?.first()?.substringAfter('=') })
            onNodeWithContentDescription("Password").performTextReplacement("secret")
            onNodeWithText("Sign in").performClick()
            await("the notice", { "notice=${app.notice} at ${app.navigator.current}" }) { shows("Living Room is connected again.") }
            assertEquals(Destination.Sources, app.navigator.current, "back where the user started")
            assertEquals(id, world.library.sources().single { it.type == "home" }.id)
        }
    }

    @Test
    fun onAPhoneTheFormIsAPageOfItsOwnWithinTheScreen() {
        val (world, platform) = world()
        screen(world, platform, width = 412, height = 915) { app ->
            awaitText("Recently Added")
            onNodeWithContentDescription("Media sources").performTouchInput { click() }
            awaitText("Add media")
            save("phone-sources")
            onNodeWithText("Add media").performTouchInput { click() }
            awaitText("Network share")
            save("phone-add-media")
            onNodeWithText("Jellyfin").performTouchInput { click() }
            awaitText("Connect to Jellyfin")
            for (field in listOf("Server address", "User name", "Password")) {
                val bounds = onNodeWithContentDescription(field).fetchSemanticsNode().boundsInRoot
                assertTrue(bounds.left >= 0f && bounds.right <= 412f && bounds.bottom < 700f, "$field fits and sits above where a keyboard rises ($bounds)")
            }
            save("phone-connect")
            // The way back is in the top corner, as on every page reached from somewhere.
            onNodeWithContentDescription("Back").performTouchInput { click() }
            await("back to the choice") { app.navigator.current == Destination.AddSource }
        }
    }

    @Test
    fun onATabletTheSamePagesKeepToAReadableColumn() {
        val (world, platform) = world()
        screen(world, platform, width = 1280, height = 800) { app ->
            awaitText("Recently Added")
            runOnIdle { app.navigator.open(Destination.Sources) }
            awaitText("Add media")
            val row = onNodeWithText("Media").fetchSemanticsNode().boundsInRoot
            assertTrue(row.left > 280f, "a column in the middle, not a row across the whole screen ($row)")
            save("tablet-sources")
            runOnIdle { app.navigator.open(Destination.Connect(ConnectionKind.SERVER)) }
            awaitText("Connect to Jellyfin")
            save("tablet-connect")
        }
    }

    @Test
    fun aRemoteReachesEveryStepAndTheTelevisionKeyboardDoesTheTyping() {
        val (world, _) = world()
        val tv = FakePlatform(television = true, connections = SourceConnections(world.library, world.registry, listOf(HomeNetwork.server, HomeNetwork.share)))
        television(world, tv) { app ->
            awaitText("Recently Added")
            runOnIdle { app.navigator.open(Destination.Sources) }
            await("focus on Add media") { focusedIs("Add media") }
            save("tv-sources")
            remote(app, Key.DPAD_CENTER)
            await("the choice, starting on the folder") { app.navigator.current == Destination.AddSource && focusedIs("A folder on this device") }
            save("tv-add-media")
            remote(app, Key.ARROW_DOWN)
            remote(app, Key.DPAD_CENTER)
            await("the form, on its first field") { app.navigator.current is Destination.Connect && onAllNodes(isFocused() and hasContentDescription("Server address")).fetchSemanticsNodes().isNotEmpty() }
            assertTrue(app.textInputActive)
            // What the television's keyboard types arrives in the field; down goes on to the next one.
            onNodeWithContentDescription("Server address").performTextInput("http://home.local:8096")
            remote(app, Key.ARROW_DOWN)
            await("the name field") { onAllNodes(isFocused() and hasContentDescription("User name")).fetchSemanticsNodes().isNotEmpty() }
            onNodeWithContentDescription("User name").performTextInput("alex")
            remote(app, Key.ARROW_DOWN)
            onNodeWithContentDescription("Password").performTextInput("secret")
            save("tv-connect")
            remote(app, Key.ARROW_DOWN)
            await("focus on Connect", { onAllNodes(isFocused()).fetchSemanticsNodes().map { it.config.toString() + " " + it.boundsInRoot } }) { focusedIs("Connect") }
            remote(app, Key.DPAD_CENTER)
            await("connected, and back on the sources") { app.navigator.current == Destination.Sources }
            await("the server listed") { shows("Living Room") }
            await("focus somewhere on the page") { onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty() }
            assertNotNull(world.library.sources().singleOrNull { it.type == "home" })
            // Back, step by step, all the way out.
            remote(app, Key.BACK)
            await("home") { app.navigator.current == Destination.Home }
        }
    }

    private fun DesktopComposeUiTest.focusedIs(text: String) = onAllNodes(isFocused() and hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() ||
        onAllNodes(isFocused()).fetchSemanticsNodes().any { node -> node.config.toString().contains(text) }
}
