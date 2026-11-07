package dev.reflux.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import dev.reflux.core.input.Key
import dev.reflux.core.model.Movie
import dev.reflux.core.model.Show
import dev.reflux.ui.state.Destination
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Identify on every input: reached from a details page, searching the catalog, telling results apart, choosing,
 * and taking it back. The catalog is a fixture; nothing here reaches a real service.
 */
@OptIn(ExperimentalTestApi::class)
class IdentifyUiTest {
    @AfterTest
    fun catalogReachableAgain() {
        FakeCatalog.unreachable = false
    }

    private fun androidx.compose.ui.test.DesktopComposeUiTest.has(text: String) = onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun dune(world: TestWorld) = world.movie("Dune").item.id

    @Test
    fun aWrongMatchIsSetRightFromItsDetailsPage() {
        val world = populated()
        val platform = FakePlatform()
        screen(world, platform) { app ->
            val dune = dune(world)
            app.navigator.open(Destination.Details(dune))
            awaitText("Identify")
            save("identify-details")
            onNodeWithText("Identify").performScrollTo().performClick()

            // The work's own name is searched as the page opens, and both films of that name are offered, told apart.
            awaitText("Identify this movie")
            await("results") { has("1984") && has("2021") }
            assertTrue(has("Dune (2021)"), "the search starts from the film's own name and year")
            assertTrue(has("Current match"), "the film it is matched to now says so")
            assertTrue(has("A duke's son leads desert warriors"), "what each is about tells them apart")
            // Results come with their own small posters (the catalog's result size), decoded at the smallest size
            // the loader keeps, never the work's full-size artwork.
            await("result posters") { platform.artwork.served.count { "art://result/" in it.first } == 2 }
            val posters = platform.artwork.served.filter { "art://result/" in it.first }
            assertTrue(posters.all { it.second <= 160 }, "result posters were asked for small: $posters")
            save("identify-results")

            onNodeWithText("1984", substring = true).performScrollTo().performClick()
            await("the corrected film's page", { app.navigator.current }) {
                val current = app.navigator.current
                current is Destination.Details && current.itemId != dune
            }
            val corrected = (app.navigator.current as Destination.Details).itemId
            assertEquals(1984, (world.library.entry(corrected)!!.item as Movie).year)
            awaitText("Directed by David Lynch")
            // Back does not return to the search, nor to the page of what it was before.
            app.navigator.back()
            assertTrue(app.navigator.current !is Destination.Identify && (app.navigator.current as? Destination.Details)?.itemId != dune)
        }
    }

    @Test
    fun theSameQueryIsAskedOnceAndFailuresAreSaid() {
        val world = populated()
        screen(world) { app -> searchesAndFailures(app, dune(world)) }
    }

    private fun androidx.compose.ui.test.DesktopComposeUiTest.searchesAndFailures(app: dev.reflux.ui.state.AppState, dune: dev.reflux.core.model.MediaId) {
        app.navigator.open(Destination.Identify(dune))
        await("results") { has("1984") }
        val asked = FakeCatalog.searches.size

        // Asking again for what is already shown does not ask the catalog again.
        onNode(hasText("Search") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performClick()
        onNode(hasText("Search") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performClick()
        waitForIdle()
        assertEquals(asked, FakeCatalog.searches.size)

        onNodeWithContentDescription("Title").performTextReplacement("Nothing Like It At All")
        onNode(hasText("Search") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performClick()
        await("nothing found to be said") { has("Nothing was found for “Nothing Like It At All”.") }

        // Offline: said, with a way to try again, and the library left as it was.
        FakeCatalog.unreachable = true
        onNodeWithContentDescription("Title").performTextReplacement("Dune 1984")
        onNode(hasText("Search") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performClick()
        await("the catalog being out of reach to be said") { has("The catalog couldn't be reached") }
        save("identify-unreachable")
        FakeCatalog.unreachable = false
        onNodeWithText("Try again").performClick()
        await("results again") { has("David Lynch") || has("A duke's son") }
    }

    @Test
    fun leavingChangesNothingAndAChoiceCanBeTakenBack() {
        val world = populated()
        screen(world) { app ->
            val dune = dune(world)
            app.navigator.open(Destination.Details(dune))
            app.navigator.open(Destination.Identify(dune))
            await("results") { has("1984") }
            // Cancelling leaves the film as it was.
            onNodeWithText("Cancel").performScrollTo().performClick()
            await("its page again") { app.navigator.current == Destination.Details(dune) }
            assertEquals(2021, (world.library.entry(dune)!!.item as Movie).year)

            app.navigator.open(Destination.Identify(dune))
            await("results") { has("1984") }
            assertFalse(has("Let Reflux identify it"), "nothing to take back yet")
            onNodeWithText("1984", substring = true).performScrollTo().performClick()
            await("the corrected film") { (app.navigator.current as? Destination.Details)?.itemId?.let { it != dune } == true }
            val corrected = (app.navigator.current as Destination.Details).itemId

            app.navigator.open(Destination.Identify(corrected))
            awaitText("Let Reflux identify it")
            onNodeWithText("Let Reflux identify it").performScrollTo().performClick()
            await("the film as its files say") { app.navigator.current == Destination.Details(dune) }
            assertEquals(2021, (world.library.entry(dune)!!.item as Movie).year)
        }
    }

    @Test
    fun aShowRefluxIsUnsureOfSaysSoAndOffersIdentify() {
        val world = TestWorld().also { it.file("TV/S01E01.mkv"); it.file("TV/S01E02.mkv") }
        kotlinx.coroutines.runBlocking { world.pipeline.refresh() }
        screen(world) { app ->
            val show = world.library.shows().single()
            assertEquals("TV", show.item.title)
            app.navigator.open(Destination.Details(show.item.id))
            awaitText("Reflux isn't sure what this is.")
            save("identify-details-unsure")
            onNodeWithText("Identify").performClick()
            awaitText("Identify this show")
            onNodeWithContentDescription("Title").performTextReplacement("Breaking Bad")
            onNode(hasText("Search") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performClick()
            await("the show") { has("2008") }
            onNodeWithText("2008", substring = true).performScrollTo().performClick()
            await("the identified show") { world.library.shows().singleOrNull()?.item?.title == "Breaking Bad" }
            assertTrue((world.library.shows().single().item as Show).year == 2008)
            await("its episodes' own titles") { has("Pilot") }
        }
    }

    @Test
    fun withoutACatalogThereIsNoIdentify() {
        // A build without the provider's credential cannot search: nothing offers what cannot be done.
        val world = populated(metadata = false)
        screen(world) { app ->
            app.navigator.open(Destination.Details(world.movie("Heat").item.id))
            awaitText("Versions")
            assertFalse(has("Identify"))
            assertFalse(app.canIdentify)
        }
    }

    @Test
    fun aRemoteSearchesAndChoosesWithoutAPointer() {
        val world = populated()
        television(world) { app ->
            val dune = dune(world)
            app.navigator.open(Destination.Identify(dune))
            await("results") { has("1984") }
            await("focus in the search field") { onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty() }
            // Down from the search reaches the results, one at a time, and OK chooses.
            remote(app, Key.ARROW_DOWN)
            await("focus on a result") { onAllNodes(isFocused() and hasText("Dune", substring = true)).fetchSemanticsNodes().isNotEmpty() }
            var steps = 0
            while (onAllNodes(isFocused() and hasText("1984", substring = true)).fetchSemanticsNodes().isEmpty() && steps++ < 4) remote(app, Key.ARROW_DOWN)
            save("tv-identify")
            remote(app, Key.DPAD_CENTER)
            await("the corrected film") { (app.navigator.current as? Destination.Details)?.itemId?.let { it != dune } == true }
            assertEquals(1984, (world.library.entry((app.navigator.current as Destination.Details).itemId)!!.item as Movie).year)
        }
    }
}
