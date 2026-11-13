package dev.reflux.library

import dev.reflux.core.model.Episode
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.ProfileId
import dev.reflux.core.model.SourceId
import dev.reflux.core.model.StableIds
import dev.reflux.core.profile.PinCheck
import dev.reflux.core.playback.SubtitlePreference
import dev.reflux.core.profile.PinPolicy

/**
 * A local viewer of this installation (docs/WATCH_STATE.md, "Profiles"): whose watch history, favorites,
 * collections, pinned versions, preferences, and server accounts something is. Media truth (works, versions,
 * metadata, artwork, Identify's corrections) is the installation's and is shared; nothing a viewer does is.
 */
data class Profile(
    val id: ProfileId,
    val name: String,
    val active: Boolean,
    /** Whether opening it asks for its PIN. */
    val hasPin: Boolean,
    /** While set and in the future, its PIN cannot be tried. */
    val lockedUntilEpochMs: Long?,
)

/**
 * What checks a profile's PIN without the PIN being kept anywhere: a keyed digest whose key the platform holds
 * (its credential store or keystore), so that what the database keeps cannot be checked against guesses without
 * that key. A shell supplies it; without one, a profile cannot have a PIN.
 */
interface PinKey {
    /** A keyed digest (HMAC) of [data] under the platform-held key. */
    fun mac(data: ByteArray): ByteArray

    /** Fresh random bytes for a salt. */
    fun randomBytes(count: Int): ByteArray
}

/** A server address entered on this device, which every profile can sign in to with its own account. */
data class ServerEndpoint(val id: String, val type: String, val address: String, val displayName: String)

/** Makes sure there is a profile and that exactly one is active: a new library starts with one, invisible until a second. */
internal fun Library.ensureProfiles() = database.transaction {
    val all = queries.profiles().executeAsList()
    if (all.isEmpty()) {
        queries.insertProfile(ProfileId.DEFAULT.value, DEFAULT_NAME, 0, now(), 1)
    } else if (all.count { it.active != 0L } != 1) {
        queries.setActiveProfile((all.firstOrNull { it.active != 0L } ?: all.first()).id)
    }
    activeProfileCache = null
}

/** The profile everything a viewer does is about now. */
fun Library.activeProfile(): ProfileId =
    activeProfileCache ?: (queries.activeProfile().executeAsOneOrNull()?.let { ProfileId(it.id) } ?: ProfileId.DEFAULT).also { activeProfileCache = it }

/** Every profile of this installation, in their order. */
fun Library.profiles(): List<Profile> {
    val now = now()
    return queries.profiles().executeAsList().map { row ->
        Profile(
            id = ProfileId(row.id),
            name = row.name,
            active = row.active != 0L,
            hasPin = row.pin_verifier != null,
            lockedUntilEpochMs = row.pin_locked_until?.takeIf { it > now },
        )
    }
}

fun Library.profile(id: ProfileId): Profile? = profiles().firstOrNull { it.id == id }

/** Adds a profile with [name]; it starts with nothing of anyone else's, and is not made active. */
fun Library.createProfile(name: String): ProfileId = database.transactionWithResult {
    val clean = name.trim().take(MAX_NAME).ifEmpty { "Profile" }
    val existing = queries.profiles().executeAsList()
    val id = ProfileId("p" + StableIds.hash("profile:$clean:${now()}:${existing.size}"))
    queries.insertProfile(id.value, clean, (existing.maxOfOrNull { it.position } ?: -1) + 1, now(), 0)
    id
}

fun Library.renameProfile(id: ProfileId, name: String) {
    val clean = name.trim().take(MAX_NAME)
    if (clean.isNotEmpty()) queries.renameProfile(clean, id.value)
}

/**
 * Makes [id] the active profile. What the library shows changes in the same transaction: another profile's
 * accounts and their copies leave view, this one's come in. Asking for a PIN is the experience layer's, before this.
 */
fun Library.switchProfile(id: ProfileId) = database.transaction {
    if (queries.profileById(id.value).executeAsOneOrNull() == null) return@transaction
    queries.setActiveProfile(id.value)
    activeProfileCache = id
    writer.recomputeLibraries()
}

/** The sources that are [id]'s own accounts: removed (and signed out of) with the profile. */
fun Library.accountSourcesOf(id: ProfileId): List<SourceRecord> = queries.sourcesOfProfile(id.value).executeAsList().map { it.toModel() }

/**
 * Deletes a profile and everything that was its own: watch history, favorites, collections, pinned versions,
 * preferences, what its accounts' servers last said. Its accounts must have been removed first ([accountSourcesOf],
 * `SourceConnections.remove`), and its downloads ([downloadsOf a profile][profileDownloadIds]), so that the servers
 * are signed out of and the files are gone. The last profile cannot be deleted. Nothing of another profile changes.
 */
fun Library.deleteProfile(id: ProfileId) = database.transaction {
    val all = queries.profiles().executeAsList()
    if (all.size <= 1 || all.none { it.id == id.value }) return@transaction
    check(queries.sourcesOfProfile(id.value).executeAsList().isEmpty()) { "a profile's accounts are removed before the profile" }
    if (all.single { it.id == id.value }.active != 0L) {
        val next = all.first { it.id != id.value }.id
        queries.setActiveProfile(next)
        activeProfileCache = ProfileId(next)
    }
    queries.deleteWatchStatesOfProfile(id.value)
    queries.deleteWatchSyncOfProfile(id.value)
    queries.deleteFavoritesOfProfile(id.value)
    queries.deleteCollectionItemsOfProfile(id.value)
    queries.deleteCollectionsOfProfile(id.value)
    queries.deletePreferencesOfProfile(id.value)
    queries.deleteProfileSettingsOf(id.value)
    // A server everyone shares was compared with this profile's history: with it gone, with nobody's. Another
    // profile is never handed it, which would give that profile the server's record of this one.
    queries.clearSyncProfile(id.value)
    queries.deleteProfile(id.value)
    writer.recomputeLibraries()
}

/**
 * Starts [id] over, for a viewer who forgot its PIN (docs/WATCH_STATE.md, "Profiles"): its PIN goes, and with it
 * everything the PIN kept private, so that resetting reveals nothing: watch history, what its servers last said,
 * favorites, collections, pinned versions, and preferences. The profile itself, with its name, stays, usable at once.
 * Its accounts must have been removed first (`SourceConnections.remove`, which signs this device out of them) and its
 * downloads; nothing here touches a server, a media file, a shared source, a server's address, or another profile.
 */
fun Library.resetProfile(id: ProfileId) = database.transaction {
    if (queries.profileById(id.value).executeAsOneOrNull() == null) return@transaction
    check(queries.sourcesOfProfile(id.value).executeAsList().isEmpty()) { "a profile's accounts are removed before it is reset" }
    queries.deleteWatchStatesOfProfile(id.value)
    queries.deleteWatchSyncOfProfile(id.value)
    queries.deleteFavoritesOfProfile(id.value)
    queries.deleteCollectionItemsOfProfile(id.value)
    queries.deleteCollectionsOfProfile(id.value)
    queries.deletePreferencesOfProfile(id.value)
    queries.deleteProfileSettingsOf(id.value)
    queries.clearSyncProfile(id.value)
    queries.resetProfileLock(id.value)
    writer.recomputeLibraries()
}

/** Keeps server addresses known after their last account here was removed, so signing in again needs no typing. */
fun Library.keepEndpoints(endpoints: List<ServerEndpoint>) = endpoints.forEach { queries.insertEndpoint(it.id, it.type, it.address, it.displayName, now()) }

/**
 * Whether the active profile can see [itemId] at all: a film or an episode it has a copy of, a show or a season it
 * has an episode of. What a link from outside Reflux names is checked with this before anything of it is shown.
 */
fun Library.seenByActiveProfile(itemId: dev.reflux.core.model.MediaId): Boolean = when (val item = rawItem(itemId)) {
    null -> false
    is dev.reflux.core.model.Show -> queries.presentShows(null, null).executeAsList().any { it.id == item.id.value }
    is dev.reflux.core.model.Season -> queries.presentSeasonsOfShow(item.showId.value, null, null).executeAsList().any { it.id == item.id.value }
    else -> queries.itemHasVersions(itemId.value).executeAsOne()
}

/** The downloads that came from [id]'s own accounts: removed with the profile. */
fun Library.profileDownloadIds(id: ProfileId): List<String> = queries.downloadsOfProfile(id.value).executeAsList().map { it.id }

/**
 * Clears [id]'s local watch history (docs/WATCH_STATE.md, "Clearing local watch history"): every position, watched
 * mark, and play count Reflux keeps for it, and with them its Continue Watching, Next Up, and launcher row. Only
 * Reflux's own record changes: no server is told, no media, download, favorite, or other profile is touched. What
 * its servers last said is kept, so that their unchanged records are recognized as such and do not bring the
 * history back; a record they make after this moment is news as usual.
 */
fun Library.clearWatchHistory(id: ProfileId) = database.transaction {
    queries.deleteWatchStatesOfProfile(id.value)
    queries.setHistoryCleared(now(), id.value)
}

/** The profile whose watch state a source's own records are compared with, or null when there is none. */
fun Library.syncProfileOf(id: SourceId): ProfileId? = queries.sourceById(id.value).executeAsOneOrNull()?.sync_profile_id?.let(::ProfileId)

/** The sources the active profile sees: those every profile shares, and its own accounts. */
fun Library.visibleSources(): List<SourceRecord> = queries.visibleSources().executeAsList().map { it.toModel() }

// PIN ----------------------------------------------------------------------------------------------

/** Gives [id] a PIN, replacing any it had. Returns false when [pin] is not four digits. */
fun Library.setPin(id: ProfileId, pin: String, key: PinKey): Boolean {
    if (!PinPolicy.valid(pin)) return false
    val salt = key.randomBytes(SALT_BYTES)
    queries.setProfilePin(salt.toHex(), verifier(key, salt, pin), id.value)
    return true
}

/** Takes [id]'s PIN away: opening it no longer asks. */
fun Library.removePin(id: ProfileId) = queries.setProfilePin(null, null, id.value)

/**
 * Checks a PIN entered for [id]. Wrong entries are counted and kept, so restarting does not reset them; after
 * [PinPolicy.FREE_ATTEMPTS] the profile waits ([PinPolicy.lockAfter]) and nothing is checked until it has.
 * A profile without a PIN accepts anything.
 */
fun Library.checkPin(id: ProfileId, pin: String, key: PinKey): PinCheck = database.transactionWithResult {
    val row = queries.profileById(id.value).executeAsOneOrNull() ?: return@transactionWithResult PinCheck.Rejected(null)
    val salt = row.pin_salt
    val expected = row.pin_verifier
    if (salt == null || expected == null) return@transactionWithResult PinCheck.Accepted
    val now = now()
    row.pin_locked_until?.takeIf { it > now }?.let { return@transactionWithResult PinCheck.Locked(it) }
    if (PinPolicy.valid(pin) && PinPolicy.same(verifier(key, salt.fromHex(), pin), expected)) {
        queries.recordPinFailure(0, null, id.value)
        return@transactionWithResult PinCheck.Accepted
    }
    val failures = row.pin_failures.toInt() + 1
    val lockedUntil = PinPolicy.lockAfter(failures)?.let { now + it }
    queries.recordPinFailure(failures.toLong(), lockedUntil, id.value)
    PinCheck.Rejected(lockedUntil)
}

private fun verifier(key: PinKey, salt: ByteArray, pin: String): String = key.mac(salt + pin.encodeToByteArray()).toHex()

private fun ByteArray.toHex(): String = joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

private fun String.fromHex(): ByteArray = ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }

/**
 * Words a support report must never contain, whoever is watching when it is made: every title, source name,
 * connection, and profile name of the installation, another profile's included. The shared diagnostics log can carry
 * any of them, so what is removed by name is never narrowed to what the open profile sees.
 */
fun Library.privateWords(): List<String> =
    queries.everyTitle().executeAsList().flatMap { listOfNotNull(it.title, it.described, it.original_title) } +
        sources().flatMap { listOf(it.displayName, it.config) } + queries.profiles().executeAsList().map { it.name }

// Preferences ---------------------------------------------------------------------------------------

/** One of the active viewer's own preferences, or null when they never chose. */
fun Library.profileSetting(key: String): String? = queries.profileSettingValue(activeProfile().value, key).executeAsOneOrNull()

/** Keeps one of the active viewer's own preferences; null forgets it. */
fun Library.setProfileSetting(key: String, value: String?) {
    val profile = activeProfile().value
    if (value == null) queries.deleteProfileSetting(profile, key) else queries.setProfileSetting(profile, key, value)
}

/** What a subtitle choice is remembered for: a movie itself, an episode's show (a choice made in one episode carries through it). */
internal fun Library.subtitleWork(itemId: MediaId): MediaId = (item(itemId) as? Episode)?.showId ?: itemId

/** What [profile] last chose for the subtitles of the work [itemId] belongs to, or null when they never chose. Another profile's is never read. */
fun Library.subtitlePreference(itemId: MediaId, profile: ProfileId = activeProfile()): SubtitlePreference? =
    SubtitlePreference.decode(queries.profileSettingValue(profile.value, SubtitlePreference.key(subtitleWork(itemId).value)).executeAsOneOrNull())

/** Remembers [preference] as [profile]'s choice for the work [itemId] belongs to; null forgets it. Only the viewer's own choice is kept, never an automatic one. */
fun Library.rememberSubtitle(itemId: MediaId, preference: SubtitlePreference?, profile: ProfileId = activeProfile()) {
    val key = SubtitlePreference.key(subtitleWork(itemId).value)
    if (preference == null) queries.deleteProfileSetting(profile.value, key) else queries.setProfileSetting(profile.value, key, preference.encode())
}

// Server endpoints ----------------------------------------------------------------------------------

/** Server addresses entered on this device, oldest first: each profile can sign in to one without typing it again. */
fun Library.endpoints(): List<ServerEndpoint> =
    queries.endpoints().executeAsList().map { ServerEndpoint(it.id, it.type, it.address, it.display_name) }

/** The endpoint for [address] of [type], made if it is new. Addresses are compared as the adapter normalized them. */
internal fun Library.endpointFor(type: String, address: String, displayName: String): String {
    val id = "e" + StableIds.hash("$type\u0000${address.trim().trimEnd('/').lowercase()}")
    queries.insertEndpoint(id, type, address, displayName, now())
    return id
}

private const val DEFAULT_NAME = "Main"
private const val MAX_NAME = 40
private const val SALT_BYTES = 16
