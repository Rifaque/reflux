package dev.reflux.library

import dev.reflux.core.identify.ParsedKind
import dev.reflux.core.identify.ParsedMedia
import dev.reflux.core.model.Availability
import dev.reflux.core.model.StableIds
import dev.reflux.core.playback.Container
import dev.reflux.core.playback.StreamInfo
import dev.reflux.core.source.CatalogEntry
import dev.reflux.core.source.CatalogSource
import dev.reflux.core.source.CatalogVersion
import dev.reflux.core.source.Connection
import dev.reflux.core.source.ConnectionKind
import dev.reflux.core.source.ConnectionRequest
import dev.reflux.core.source.PlaybackTarget
import dev.reflux.core.source.SourceCapability
import dev.reflux.core.source.SourceConnector
import dev.reflux.core.source.SourceDescriptor
import dev.reflux.core.source.SourceLocality
import dev.reflux.core.source.SourceUserState
import dev.reflux.core.source.WatchStateSyncSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Fixtures for the profile tests (M8; docs/WATCH_STATE.md, "Profiles"), kept for M12's qualification to reuse: a media
 * server where each person signs in with their own account, its connector, and a PIN key held in memory.
 */
internal object AccountServers {
    /** What every account's server says, by account: shared by the source instances the registry recreates. */
    val catalogs = mutableMapOf<String, MutableList<CatalogEntry>>()

    /** What each account's server was told, by account, in order. */
    val told = mutableMapOf<String, MutableList<String>>()

    fun reset() {
        catalogs.clear()
        told.clear()
    }

    fun movie(account: String, key: String, title: String, year: Int, state: SourceUserState? = null) {
        catalogs.getOrPut(account) { mutableListOf() } += CatalogEntry(
            ParsedMedia(ParsedKind.MOVIE, title, year),
            listOf(CatalogVersion("$account/$key", 1, 1, StreamInfo(Container.MATROSKA, durationMs = 6_000_000))),
            userState = state,
        )
    }

    fun episode(account: String, key: String, show: String, season: Int, episode: Int) {
        catalogs.getOrPut(account) { mutableListOf() } += CatalogEntry(
            ParsedMedia(ParsedKind.EPISODE, show, 2022, season = season, episode = episode),
            listOf(CatalogVersion("$account/$key", 1, 1, StreamInfo(Container.MATROSKA, durationMs = 3_000_000))),
        )
    }

    /** Replaces what the server says about [key] for [account]. */
    fun setState(account: String, key: String, state: SourceUserState?) {
        val list = catalogs.getValue(account)
        val index = list.indexOfFirst { it.versions.single().path == "$account/$key" }
        list[index] = list[index].copy(userState = state)
    }
}

/** One person's account on the home server at [address]. */
internal class AccountServer(val address: String, val account: String) : CatalogSource, WatchStateSyncSource {
    override val descriptor = SourceDescriptor(
        StableIds.sourceId("acct", "$address/$account"), "acct", "Home server", SourceLocality.LOCAL_NETWORK,
        setOf(SourceCapability.PROVIDES_CATALOG, SourceCapability.WATCH_STATE_SYNC),
    )
    override suspend fun availability() = Availability.AVAILABLE
    override suspend fun playbackTarget(path: String) = PlaybackTarget("acct://$address/$path")
    override fun catalog(): Flow<CatalogEntry> = AccountServers.catalogs[account].orEmpty().toList().asFlow()
    private fun tell(what: String) {
        AccountServers.told.getOrPut(account) { mutableListOf() } += what
    }
    override suspend fun reportProgress(path: String, positionMs: Long, paused: Boolean) = tell("progress $path")
    override suspend fun reportStopped(path: String, positionMs: Long) = tell("stopped $path")
    override suspend fun updatePosition(path: String, positionMs: Long) = tell("position $path")
    override suspend fun setPlayed(path: String, played: Boolean) = tell("played $path $played")
}

/** Signs in to the home server as a name; any name is an account of its own. */
internal object AccountConnector : SourceConnector {
    override val type = "acct"
    override val kind = ConnectionKind.SERVER
    override val product = "Home"
    override val addressExample = "acct://home"
    override val anonymous = false
    override fun accepts(address: String) = address.startsWith("acct://")
    override suspend fun connect(request: ConnectionRequest): Connection {
        val account = request.username ?: return Connection.Failed(dev.reflux.core.source.ConnectionProblem.SIGN_IN_REQUIRED)
        return Connection.Established(AccountServer(request.address, account), config(request.address, account))
    }
    override fun describe(config: String) = config.lines().let { ConnectionRequest(it[0].substringAfter('='), it[1].substringAfter('=')) }
    override fun account(config: String): String = config.lines()[1].substringAfter('=')

    fun config(address: String, account: String) = "address=$address\naccount=$account"

    val registry get() = SourceRegistry(mapOf("acct" to { record -> describe(record.config).let { AccountServer(it.address, it.username!!) } }))
}

/** A PIN key with a fixed key in memory, for tests; the shells use [SealedPinKey]. */
internal class MemoryPinKey : PinKey {
    private val key = ByteArray(32) { it.toByte() }
    private var counter = 0
    override fun mac(data: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256"))
        doFinal(data)
    }
    override fun randomBytes(count: Int) = ByteArray(count) { (it + counter++).toByte() }
}
