package dev.reflux.cli

import dev.reflux.core.model.Availability
import dev.reflux.core.model.Episode
import dev.reflux.core.model.MediaItem
import dev.reflux.core.model.Movie
import dev.reflux.core.model.Show
import dev.reflux.core.playback.PlaybackController
import dev.reflux.core.playback.PlayerStatus
import dev.reflux.core.playback.TrackPreferences
import dev.reflux.library.LibraryEntry
import dev.reflux.library.SyncingWatchReporter
import dev.reflux.library.VersionInfo
import dev.reflux.library.diagnostics
import dev.reflux.library.planPlayback
import dev.reflux.playback.mpv.MpvPlayer
import dev.reflux.sources.jellyfin.JellyfinSource
import dev.reflux.sources.local.LocalFolderSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlin.system.exitProcess

private const val USAGE = """Reflux developer CLI - runs the real Reflux engine against your media.

Usage: reflux <command> [arguments]

  add <folder> [name]          Add a local media folder (read-only; files are never modified)
  jellyfin <server> <user>     Sign in to a Jellyfin server (password from REFLUX_JELLYFIN_PASSWORD or prompt)
  sources                      List sources and whether they are reachable
  refresh                      Scan all sources, probe new files, fetch metadata and artwork
  library                      List movies and shows
  search <query...>            Smart search, e.g.  reflux search 4k movies I haven't watched
  info <query...>              Versions of the best match and which one would play here
  play <query...>              Play the best match in an mpv window (use --from-start to ignore resume)
  doctor                       Library health: weak identification, missing episodes, offline sources

Data lives in the platform data directory (override with REFLUX_HOME).
Metadata needs a TMDB credential in REFLUX_TMDB_TOKEN."""

fun main(args: Array<String>) {
    val command = args.firstOrNull() ?: run {
        println(USAGE)
        return
    }
    val rest = args.drop(1)
    val env = AppEnvironment()
    val ok = runBlocking {
        when (command) {
            "add" -> add(env, rest)
            "jellyfin" -> jellyfin(env, rest)
            "sources" -> sources(env)
            "refresh" -> refresh(env)
            "library" -> library(env)
            "search" -> search(env, rest.joinToString(" "))
            "info" -> info(env, rest.joinToString(" "))
            "play" -> play(env, rest.filter { it != "--from-start" }.joinToString(" "), resume = "--from-start" !in rest)
            "doctor" -> doctor(env)
            "help", "--help", "-h" -> { println(USAGE); true }
            else -> { System.err.println("Unknown command '$command'.\n\n$USAGE"); false }
        }
    }
    exitProcess(if (ok) 0 else 1)
}

private suspend fun add(env: AppEnvironment, args: List<String>): Boolean {
    val folder = args.firstOrNull()?.let { Path.of(it).toAbsolutePath().normalize() } ?: return usage("add <folder> [name]")
    if (!Files.isDirectory(folder)) return fail("Not a folder: $folder")
    val source = LocalFolderSource(folder, args.getOrNull(1) ?: folder.fileName?.toString() ?: folder.toString())
    env.library.addSource(source, folder.toString())
    env.registry.register(source)
    println("Added ${source.descriptor.displayName} ($folder). Scanning...")
    return refresh(env, source.descriptor.id)
}

private suspend fun jellyfin(env: AppEnvironment, args: List<String>): Boolean {
    if (args.size < 2) return usage("jellyfin <server> <user>")
    val password = System.getenv("REFLUX_JELLYFIN_PASSWORD")
        ?: System.console()?.readPassword("Password for ${args[1]}: ")?.concatToString()
        ?: return fail("No password: set REFLUX_JELLYFIN_PASSWORD or run in a terminal.")
    val credentials = try {
        JellyfinSource.connect(args[0], args[1], password, env.http, env.deviceId, deviceName = "Reflux CLI")
    } catch (e: Exception) {
        return fail("Could not sign in: ${e.message}")
    }
    val source = JellyfinSource(credentials, env.http, "Jellyfin (${args[1]})")
    env.library.addSource(source, credentials.encode())
    env.registry.register(source)
    println("Signed in to ${credentials.serverUrl}. Reading the catalog...")
    return refresh(env, source.descriptor.id)
}

private fun sources(env: AppEnvironment): Boolean {
    val sources = env.library.sources()
    if (sources.isEmpty()) println("No sources yet. Add one with: reflux add <folder>")
    for (source in sources) {
        val state = when (source.availability) {
            Availability.AVAILABLE -> "online"
            Availability.UNAVAILABLE -> "offline"
            Availability.UNKNOWN -> "not checked"
        }
        println("${source.displayName}  [${source.type}, $state]  ${source.id}")
    }
    return true
}

private suspend fun refresh(env: AppEnvironment, only: dev.reflux.core.model.SourceId? = null): Boolean {
    val reports = env.pipeline.refresh(only)
    for (report in reports) {
        val name = env.library.source(report.sourceId)?.displayName ?: report.sourceId.value
        val skipped = report.skipped.entries.joinToString { "${it.value} ${it.key.name.lowercase()}" }.ifEmpty { "none" }
        println("$name: ${report.status.name.lowercase()} - ${report.added} added, ${report.updated} updated, ${report.removed} removed, ${report.unchanged} unchanged; skipped: $skipped; weak identification: ${report.lowConfidence}")
    }
    if (env.prober == null) println("libmpv not found: stream details come from file names only.")
    if (env.metadata == null) println("No TMDB credential (REFLUX_TMDB_TOKEN): titles come from file and folder names; local artwork only.")
    return true
}

private fun library(env: AppEnvironment): Boolean {
    val movies = env.library.movies()
    val shows = env.library.shows()
    println("Movies (${movies.size})")
    movies.forEach { println("  ${line(it)}") }
    println("Shows (${shows.size})")
    shows.forEach { println("  ${line(it)}") }
    return true
}

private fun search(env: AppEnvironment, query: String): Boolean {
    if (query.isBlank()) return usage("search <query>")
    val result = env.library.smartSearch(query)
    val q = result.query
    if (q.structured) {
        val parts = listOfNotNull(
            q.kinds.takeIf { it.isNotEmpty() }?.joinToString("/") { it.name.lowercase() + "s" },
            q.watched?.let { if (it) "watched" else "unwatched" },
            "in progress".takeIf { q.inProgress },
            "favorites".takeIf { q.favorite },
            q.minHeight?.let { "${it}p+" },
            q.dynamicRange?.name?.replace('_', ' ')?.lowercase(),
            "HDR".takeIf { q.hdr },
            q.genres.takeIf { it.isNotEmpty() }?.joinToString(),
            q.people.takeIf { it.isNotEmpty() }?.joinToString(),
            q.years?.let { "${it.first}-${it.last}" },
            q.maxRuntimeMinutes?.let { "<= $it min" },
            q.minRuntimeMinutes?.let { ">= $it min" },
            q.text.takeIf { it.isNotBlank() }?.let { "\"$it\"" },
        )
        println("Understood: ${parts.joinToString(" | ")}")
    }
    if (result.results.isEmpty()) println("Nothing found.")
    result.results.forEach { println("  ${line(it)}") }
    return true
}

private fun info(env: AppEnvironment, query: String): Boolean {
    val entry = bestMatch(env, query) ?: return fail("Nothing matches \"$query\".")
    println(line(entry))
    entry.metadata?.overview?.let { println("  $it") }
    val playable = playableOf(env, entry.item) ?: return fail("Nothing to play.")
    val selection = env.library.selectVersion(playable.id, env.deviceCapabilities())
    selection.ranked.forEachIndexed { index, ranked ->
        val marker = if (index == 0 && ranked.playable) ">" else " "
        val issues = ranked.assessment.issues.joinToString { it.name.lowercase().replace('_', ' ') }.ifEmpty { "plays optimally" }
        val info = env.library.versions(playable.id).first { it.version.id == ranked.version.id }
        println("  $marker ${describe(info)} - $issues")
    }
    selection.decidedBy?.let { println("  chosen by: ${it.name.lowercase().replace('_', ' ')}") }
    return true
}

private suspend fun play(env: AppEnvironment, query: String, resume: Boolean): Boolean {
    val entry = bestMatch(env, query) ?: return fail("Nothing matches \"$query\".")
    val playable = playableOf(env, entry.item) ?: return fail("Nothing to play.")
    val device = env.deviceCapabilities()
    val plan = env.library.planPlayback(playable.id, device, env.registry::resolve, resume = resume)
        ?: return fail("No playable version right now (is the source offline?).")
    val player = MpvPlayer.create(
        device,
        // A standalone mpv window with mpv's own controls, since the CLI has no UI of its own.
        mapOf("input-default-bindings" to "yes", "input-vo-keyboard" to "yes", "osc" to "yes", "force-window" to "yes"),
    ) ?: return fail("libmpv is not installed.")
    println("Playing ${plan.request.title}" + (plan.request.startPositionMs?.let { " from ${it / 60_000} min" } ?: ""))
    player.use {
        kotlinx.coroutines.coroutineScope {
            val reporter = SyncingWatchReporter(env.library, plan.version.version, plan.source, this)
            val languages = listOf(Locale.getDefault().language).filter { it.isNotBlank() }.ifEmpty { listOf("en") }
            val controller = PlaybackController(player, playable.id, reporter, TrackPreferences(languages))
            controller.start(this, plan.request)
            player.state.first { it.status == PlayerStatus.PLAYING || it.status == PlayerStatus.ERROR }
            val end = player.state.first { it.status == PlayerStatus.ENDED || it.status == PlayerStatus.ERROR || it.status == PlayerStatus.IDLE }
            end.error?.let { println("Playback error: $it") }
            controller.stop()
        }
    }
    return true
}

private fun doctor(env: AppEnvironment): Boolean {
    val report = env.library.diagnostics()
    println("Weak identification (${report.lowConfidence.size}) - fix with Identify:")
    report.lowConfidence.forEach { println("  ${it.version.location.path}") }
    println("No metadata match (${report.unmatched.size}):")
    report.unmatched.forEach { println("  ${line(it)}") }
    println("Several versions (${report.multipleVersions.size}):")
    report.multipleVersions.forEach { println("  ${line(it)}") }
    println("Missing episodes (${report.missingEpisodes.size}):")
    report.missingEpisodes.forEach { println("  ${it.showTitle} ${it.season.title}: ${it.missing.joinToString()}") }
    println("Missing posters (${report.missingArtwork.size})")
    println("Offline sources (${report.unavailableSources.size}):")
    report.unavailableSources.forEach { println("  ${it.displayName}") }
    return true
}

private fun bestMatch(env: AppEnvironment, query: String): LibraryEntry? =
    if (query.isBlank()) null else env.library.smartSearch(query).results.firstOrNull()

/** Movies and episodes play themselves; a show plays its Next Up episode, or its first one. */
private fun playableOf(env: AppEnvironment, item: MediaItem): MediaItem? = when (item) {
    is Movie, is Episode -> item
    is Show -> env.library.showDetail(item.id)?.let { detail ->
        detail.nextUp ?: detail.seasons.firstOrNull { it.season.number > 0 }?.episodes?.firstOrNull()?.item
    }
    else -> null
}

private fun line(entry: LibraryEntry): String {
    val item = entry.item
    val title = when (item) {
        is Movie -> item.title + (item.year?.let { " ($it)" } ?: "")
        is Show -> item.title + (item.year?.let { " ($it)" } ?: "")
        else -> item.title
    }
    val flags = listOfNotNull(
        "offline".takeIf { entry.availability == Availability.UNAVAILABLE },
        "watched".takeIf { entry.watchState?.completed == true },
        entry.watchState?.takeIf { it.inProgress }?.let { "at ${it.positionMs / 60_000} min" },
        "favorite".takeIf { entry.favorite },
    )
    return title + if (flags.isEmpty()) "" else "  [${flags.joinToString()}]"
}

private fun describe(info: VersionInfo): String {
    val stream = info.version.stream
    val video = stream.video
    val parts = listOfNotNull(
        video?.nominalHeight?.let { "${it}p" },
        video?.codec?.name?.takeIf { it != "UNKNOWN" },
        video?.dynamicRange?.takeIf { it.name != "SDR" }?.name?.replace('_', ' '),
        stream.audio.firstOrNull()?.let { audio -> audio.codec.name + (audio.channels?.let { " ${it}ch" } ?: "") },
        info.version.edition,
        info.locality.name.lowercase().replace('_', ' '),
    )
    return "${info.version.location.path} (${parts.joinToString(", ")})"
}

private fun usage(syntax: String): Boolean = fail("Usage: reflux $syntax")

private fun fail(message: String): Boolean {
    System.err.println(message)
    return false
}
