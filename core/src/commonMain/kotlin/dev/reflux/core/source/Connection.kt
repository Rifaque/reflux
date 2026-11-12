package dev.reflux.core.source

/** What the user is connecting to, in their terms. Which adapter serves it is not their concern. */
enum class ConnectionKind {
    /** A media server that already knows what its media is and who is watching. */
    SERVER,

    /** A folder shared on the network, by a computer, a NAS, or web storage. */
    SHARE,
}

/** What the user fills in to connect. [username] and [password] are null when left empty. */
data class ConnectionRequest(val address: String, val username: String? = null, val password: String? = null)

/** Why a connection could not be made, in terms of what the user can do about it. */
enum class ConnectionProblem {
    /** What was typed is not an address of this kind. Nothing was contacted. */
    INVALID_ADDRESS,

    /** Nothing answered: the device is off, on another network, or the address is wrong. */
    UNREACHABLE,

    /** Something answered, but it is not what was asked for: no such server, or no such shared folder. */
    NOT_FOUND,

    /** It needs a name and password, and none was given. */
    SIGN_IN_REQUIRED,

    /** The name or password was not accepted. */
    AUTHENTICATION,

    /** The account is known but may not read this. */
    PERMISSION_DENIED,

    /** It answered, and could not serve the request (a server that is starting up, or failing). */
    SERVER_ERROR,
    UNKNOWN,
}

/** What came of trying to connect. */
sealed interface Connection {
    /**
     * It worked. [source] is ready to use, and [config] is what recreates it later (see `SourceRegistry`).
     * A password that was only needed to sign in is not part of it.
     */
    class Established(val source: MediaSource, val config: String) : Connection

    /** [detail] is for diagnostics, never for the screen, and carries no credentials. */
    data class Failed(val problem: ConnectionProblem, val detail: String? = null) : Connection
}

/**
 * How a kind of source is added by someone typing an address: the one thing every adapter that is reached
 * over a network has to offer for the experience layer to set it up, without that layer knowing the adapter.
 *
 * A connector validates, contacts, and signs in; it registers nothing. A platform offers the connectors it
 * has; one that is not offered is simply a kind of source that cannot be added there.
 */
interface SourceConnector {
    /** The adapter type its sources carry (see [SourceDescriptor.type]). */
    val type: String

    val kind: ConnectionKind

    /** The product's own name where it has one ("Jellyfin"); null for something generic, such as a shared folder. */
    val product: String?

    /** An address as a user would type one, to show what is expected. */
    val addressExample: String

    /** Whether a connection can be made without a name and password. */
    val anonymous: Boolean

    /** Whether [address] is of the form this connector handles. Decides between connectors of one [kind]. */
    fun accepts(address: String): Boolean

    /** Never throws for anything a user can cause: every such outcome is a [Connection.Failed]. */
    suspend fun connect(request: ConnectionRequest): Connection

    /** What a stored [config] connects to, for showing it and for changing it. Never the password or a token. */
    fun describe(config: String): ConnectionRequest?

    /**
     * Whose account [config] signs in as, when it is one person's account on a server (a Jellyfin user, a Plex
     * account or token): such a connection belongs to the profile that made it and is never seen by another
     * (docs/WATCH_STATE.md, "Profiles"). Null for a connection everyone on the device shares: a folder, a share,
     * a server that lets the local network in without signing in. Never a password or a token.
     */
    fun account(config: String): String? = null

    /**
     * Libraries a connection stored before M3 left out of reading altogether. Inclusion now belongs to the library
     * (docs/NAVIGATION.md), so these are moved there once and cleared with [withoutExcludedSections].
     */
    fun excludedSections(config: String): Set<String> = emptySet()

    /** [config] without the libraries it used to leave out (see [excludedSections]); null when nothing changes. */
    fun withoutExcludedSections(config: String): String? = null

    /**
     * Ends what [config] signed in to on the other side, so that what it holds stops working there: this
     * device's session on a server, and nothing else of the account's. Returns false when there is nothing
     * of the kind to end (the default: a share keeps no session). [keeping] is a connection that stays in
     * use; whatever it shares with [config] is left open.
     *
     * Throws when it could not be confirmed (unreachable, refused, an unexpected answer).
     */
    suspend fun signOut(config: String, keeping: String? = null): Boolean = false
}

/** What a part of a source holds, in the user's terms. */
enum class SectionKind { MOVIES, SHOWS, MIXED }

/** A part of a source that can be included or left out on its own, such as one of a media server's libraries. */
data class SourceSection(val id: String, val name: String, val kind: SectionKind)

/**
 * A source made of libraries (a media server's). Every library is read, so each can be browsed on its own;
 * which of them take part in the unified library is the library's to decide, never the adapter's
 * (docs/NAVIGATION.md). A catalog source says which library each entry is in ([CatalogEntry.section]).
 */
interface SectionedSource : MediaSource {
    /** The libraries there are now. Throws [SourceUnavailableException] when the source cannot be reached. */
    suspend fun sections(): List<SourceSection>
}
