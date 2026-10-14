package dev.reflux.ui.state

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.reflux.core.model.Availability
import dev.reflux.core.model.SourceId
import dev.reflux.core.source.ConnectionKind
import dev.reflux.core.source.ConnectionProblem
import dev.reflux.core.source.ConnectionRequest
import dev.reflux.library.ConnectResult
import dev.reflux.library.Library
import dev.reflux.library.SourceConnections
import dev.reflux.library.SourceRecord
import dev.reflux.library.currentTime
import dev.reflux.library.sourceContents
import dev.reflux.library.userSources
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext

/** One source as the sources page shows it: what it is and whether it is there, then what there is to know. */
@Immutable
data class SourceRow(
    val id: SourceId,
    val name: String,
    /** "Folder", "Jellyfin", "Network share". */
    val kindLabel: String,
    /** Null for a folder, which is not connected by address. */
    val kind: ConnectionKind?,
    val connected: Boolean,
    /** "12 movies · 3 shows". */
    val contents: String?,
    /** "Updated 5 minutes ago", or when it was last there. */
    val updated: String?,
    /** Where it is, as it would be typed, without any password. */
    val address: String?,
    val account: String?,
)

/** Every source, in the order they were added, and whether anything can be connected by address here. */
@Immutable
data class SourcesModel(val rows: List<SourceRow>, val canConnect: Boolean)

internal fun Library.sourcesModel(connections: SourceConnections?): SourcesModel {
    val now = currentTime()
    return SourcesModel(
        rows = userSources().map { record ->
            val info = connections?.describe(record)
            val contents = sourceContents(record.id)
            SourceRow(
                id = record.id,
                name = record.displayName,
                kindLabel = when (info?.kind) {
                    ConnectionKind.SERVER -> info.product ?: "Media server"
                    ConnectionKind.SHARE -> "Network share"
                    null -> "Folder"
                },
                kind = info?.kind,
                connected = record.availability != Availability.UNAVAILABLE,
                contents = Formatting.contents(contents.movies, contents.shows),
                updated = if (record.availability == Availability.UNAVAILABLE) {
                    record.lastAvailableAtEpochMs?.let { "Last connected ${Formatting.ago(now - it)}" }
                } else {
                    record.lastScanAtEpochMs?.let { "Updated ${Formatting.ago(now - it)}" }
                },
                address = info?.request?.address,
                account = info?.request?.username,
            )
        },
        canConnect = connections?.kinds?.isNotEmpty() == true,
    )
}

internal fun Formatting.contents(movies: Int, shows: Int): String? = listOfNotNull(
    movies.takeIf { it > 0 }?.let { if (it == 1) "1 movie" else "$it movies" },
    shows.takeIf { it > 0 }?.let { if (it == 1) "1 show" else "$it shows" },
).joinToString(" · ").ifEmpty { null }

/** How long ago, in the words a person would use. */
internal fun Formatting.ago(ms: Long): String {
    val minutes = ms / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> if (minutes == 1L) "a minute ago" else "$minutes minutes ago"
        minutes < 24 * 60 -> (minutes / 60).let { if (it == 1L) "an hour ago" else "$it hours ago" }
        else -> (minutes / (24 * 60)).let { if (it == 1L) "yesterday" else "$it days ago" }
    }
}

/**
 * Connecting a server or a network share: what has been typed, whether it is being tried, and what went
 * wrong. The password lives here only for as long as the form does.
 */
@Stable
class SourceSetup internal constructor(
    private val app: AppState,
    private val connections: SourceConnections,
    val kind: ConnectionKind,
    /** The source being signed in to again or changed, if this is not a new one. */
    val editing: SourceRecord?,
) {
    private val known = editing?.let { connections.describe(it)?.request }

    var address by mutableStateOf(known?.address.orEmpty())
    var username by mutableStateOf(known?.username.orEmpty())
    var password by mutableStateOf("")

    /** The product being connected to, when there is one of this kind ("Jellyfin"). */
    val product: String? = connections.connectors.filter { it.kind == kind }.let { of -> of.singleOrNull()?.product ?: of.firstNotNullOfOrNull { it.product }.takeIf { kind == ConnectionKind.SERVER } }

    /** Addresses that show what is expected. */
    val examples: List<String> = connections.examples(kind)

    /** Whether a name and password may be left out. */
    val anonymous: Boolean = connections.connectors.any { it.kind == kind && it.anonymous }

    var connecting by mutableStateOf(false)
        private set

    /** What went wrong last time, in the user's words; null when nothing did. */
    var problem: String? by mutableStateOf(null)
        private set

    /** Which field [problem] is about, so the form can point there. */
    var problemField: Field? by mutableStateOf(null)
        private set

    enum class Field { ADDRESS, USERNAME, PASSWORD }

    fun connect(): Job = app.attempt(Problems.connection(ConnectionProblem.UNKNOWN, kind, product, examples)) {
        if (connecting) return@attempt
        val missing = when {
            address.isBlank() -> Field.ADDRESS
            !anonymous && username.isBlank() -> Field.USERNAME
            else -> null
        }
        if (missing != null) {
            problemField = missing
            problem = if (missing == Field.ADDRESS) Problems.ENTER_ADDRESS else Problems.ENTER_USERNAME
            return@attempt
        }
        connecting = true
        problem = null
        problemField = null
        val result = try {
            val request = ConnectionRequest(address.trim(), username.trim().ifEmpty { null }, password.ifEmpty { null })
            withContext(app.background) { connections.connect(kind, request, replacing = editing?.id) }
        } finally {
            connecting = false
        }
        when (result) {
            is ConnectResult.Connected -> {
                password = ""
                app.sourceConnected(result.sourceId, result.name, result.updated)
            }
            is ConnectResult.Failed -> {
                problem = Problems.connection(result.problem, kind, product, examples)
                problemField = when (result.problem) {
                    ConnectionProblem.SIGN_IN_REQUIRED -> Field.USERNAME
                    ConnectionProblem.AUTHENTICATION -> Field.PASSWORD
                    ConnectionProblem.INVALID_ADDRESS, ConnectionProblem.UNREACHABLE, ConnectionProblem.NOT_FOUND -> Field.ADDRESS
                    else -> null
                }
            }
        }
    }
}

internal fun Problems.connection(problem: ConnectionProblem, kind: ConnectionKind, product: String?, examples: List<String>): String {
    val server = kind == ConnectionKind.SERVER
    val thing = if (server) "the server" else "the computer or NAS"
    return when (problem) {
        ConnectionProblem.INVALID_ADDRESS ->
            (if (server) "That isn't a server address Reflux can use." else "That isn't a share address Reflux can open here.") +
                examples.takeIf { it.isNotEmpty() }?.let { " It looks like ${it.joinToString(" or ")}." }.orEmpty()
        ConnectionProblem.UNREACHABLE ->
            "Nothing answered at that address. Check that $thing is switched on and on the same network as this device, and that the address is right."
        ConnectionProblem.NOT_FOUND ->
            if (server) "Something answered at that address, but it isn't ${product?.let { "a $it server" } ?: "a media server"}. Check the address and its port."
            else "Reflux reached it, but couldn't find that shared folder. Check the folder's name."
        ConnectionProblem.SIGN_IN_REQUIRED -> if (server) "Enter the user name and password you use for this server." else "This share needs a user name and password."
        ConnectionProblem.AUTHENTICATION -> "That user name or password wasn't accepted."
        ConnectionProblem.PERMISSION_DENIED -> "This account isn't allowed to read that. Check its permissions on $thing."
        ConnectionProblem.SERVER_ERROR -> "The server answered but couldn't sign you in right now. It may still be starting up; try again in a moment."
        ConnectionProblem.UNKNOWN -> "That couldn't be connected. Try again, and if it keeps happening, the details are in Reflux's log."
    }
}
