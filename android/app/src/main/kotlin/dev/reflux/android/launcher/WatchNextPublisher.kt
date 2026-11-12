package dev.reflux.android.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.TvContractCompat.PreviewPrograms
import androidx.tvprovider.media.tv.TvContractCompat.WatchNextPrograms
import androidx.tvprovider.media.tv.WatchNextProgram as TvWatchNextProgram
import dev.reflux.android.MainActivity
import dev.reflux.library.activeProfile
import dev.reflux.library.ArtworkCache
import dev.reflux.library.Library
import dev.reflux.library.PublishedProgram
import dev.reflux.library.WatchNext
import dev.reflux.library.WatchNextArtwork
import dev.reflux.library.WatchNextChange
import dev.reflux.library.WatchNextProgram
import dev.reflux.library.WatchNextType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Keeps the launcher's Watch Next row (Android TV, Google TV) showing Reflux's Continue Watching and Next
 * Up. Each run derives the programs from the library's canonical watch state and changes only the rows
 * that differ ([WatchNext.plan]); the launcher's rows are never read back into Reflux's state.
 *
 * It runs while Reflux does, when the user's state changes and when they leave the application; there is
 * no background job. On a device without the TV provider (phones, tablets) it does nothing, and on Fire OS,
 * whose home screen does not show these rows, it does nothing either ([publishes]).
 */
class WatchNextPublisher(
    context: Context,
    private val library: Library,
    /** Where the launcher reads pictures from: the application's own artwork cache ([ArtworkProvider]). */
    private val artwork: ArtworkCache,
    private val report: (String, Throwable?) -> Unit,
) {
    private val application = context.applicationContext
    private val resolver = application.contentResolver
    private val mutex = Mutex()

    /** Works the user removed from the launcher, and when they had last played them (see [WatchNext.plan]). */
    private val dismissals = application.getSharedPreferences("watch-next-dismissed", Context.MODE_PRIVATE)

    /** Whether this device has the TV provider, where Watch Next programs are kept. */
    private val tvProvider: Boolean by lazy {
        runCatching { resolver.acquireContentProviderClient(TvContractCompat.AUTHORITY)?.also { it.close() } != null }.getOrDefault(false)
    }

    /** Whether Reflux writes Watch Next programs on this device at all ([publishes]). */
    val supported: Boolean by lazy { publishes(tvProvider, application.packageManager.hasSystemFeature(FIRE_TV_FEATURE)) }

    /** Brings the row up to date. Never throws: a launcher that refuses is reported, and Reflux carries on. */
    suspend fun publish() {
        if (!supported) return
        mutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    update()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    report("The launcher's Watch Next row could not be updated", e)
                }
            }
        }
    }

    private fun update() {
        // The active profile's, naming it; nothing for a profile with a PIN (docs/WATCH_STATE.md, "Profiles").
        val programs = WatchNext.programs(library)
        val artwork = programs.associate { it.key to it.artwork?.let { art -> artworkUri(art) } }
        // A row is compared by what it shows, the picture included: artwork that arrives later is written then.
        fun fingerprint(program: WatchNextProgram) = program.fingerprint + FIELD + artwork[program.key]
        fun row(program: WatchNextProgram) = values(program, artwork[program.key], fingerprint(program))
        val plan = WatchNext.plan(programs, published(), dismissed(), ::fingerprint)
        for (change in plan.changes) {
            when (change) {
                is WatchNextChange.Add -> resolver.insert(WatchNextPrograms.CONTENT_URI, row(change.program))
                is WatchNextChange.Update -> resolver.update(TvContractCompat.buildWatchNextProgramUri(change.rowId), row(change.program), null, null)
                is WatchNextChange.Remove -> resolver.delete(TvContractCompat.buildWatchNextProgramUri(change.rowId), null, null)
            }
        }
        if (plan.dismissed != dismissed()) {
            val mine = WatchNextDismissals.prefix(library.activeProfile())
            dismissals.edit {
                // Only this profile's are rewritten; another viewer's stay theirs. What an earlier version kept without
                // a profile is dropped: it cannot be said whose it was.
                dismissals.all.keys.filter { it.startsWith(mine) || !WatchNextDismissals.scoped(it) }.forEach(::remove)
                plan.dismissed.forEach { (key, engaged) -> putLong(mine + key, engaged) }
            }
        }
    }

    /** The cards the active profile's viewer removed from the launcher: one viewer's removal never hides another's card. */
    private fun dismissed(): Map<String, Long> = WatchNextDismissals.of(library.activeProfile(), dismissals.all)

    /** Reflux's own rows: the provider shows an application only the programs it added. */
    // The builder's setters and the program columns are the library's documented API (the Watch Next guide uses
    // them as here), declared on base types the library marks as its own; lint takes those marks for the methods.
    @SuppressLint("RestrictedApi")
    private fun published(): List<PublishedProgram> {
        val columns = arrayOf(WatchNextPrograms._ID, WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID, WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_DATA, WatchNextPrograms.COLUMN_BROWSABLE)
        return resolver.query(WatchNextPrograms.CONTENT_URI, columns, null, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        PublishedProgram(
                            rowId = cursor.getLong(0),
                            key = cursor.getString(1),
                            fingerprint = if (cursor.isNull(2)) null else cursor.getBlob(2).decodeToString(),
                            browsable = cursor.isNull(3) || cursor.getInt(3) != 0,
                        ),
                    )
                }
            }
        }.orEmpty()
    }

    @SuppressLint("RestrictedApi") // as for published()
    private fun values(program: WatchNextProgram, artwork: Uri?, fingerprint: String) = TvWatchNextProgram.Builder().apply {
        setWatchNextType(if (program.type == WatchNextType.CONTINUE) WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE else WatchNextPrograms.WATCH_NEXT_TYPE_NEXT)
        setLastEngagementTimeUtcMillis(program.lastEngagedAtEpochMs)
        setTitle(program.title)
        program.description?.let(::setDescription)
        when (program.kind) {
            WatchNextProgram.Kind.MOVIE -> setType(PreviewPrograms.TYPE_MOVIE)
            WatchNextProgram.Kind.EPISODE -> {
                setType(PreviewPrograms.TYPE_TV_EPISODE)
                program.episodeTitle?.let(::setEpisodeTitle)
                program.seasonNumber?.let(::setSeasonNumber)
                program.episodeNumber?.let(::setEpisodeNumber)
            }
        }
        program.year?.let { setReleaseDate(it.toString()) }
        program.positionMs?.let { setLastPlaybackPositionMillis(it.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) }
        program.durationMs?.let { setDurationMillis(it.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) }
        if (artwork != null) {
            setPosterArtUri(artwork)
            setPosterArtAspectRatio(
                if (program.artwork?.shape == WatchNextArtwork.Shape.WIDE) PreviewPrograms.ASPECT_RATIO_16_9 else PreviewPrograms.ASPECT_RATIO_MOVIE_POSTER,
            )
        }
        setIntent(Intent(Intent.ACTION_VIEW, WatchNextLink.of(program.itemId, program.profile).toUri(), application, MainActivity::class.java))
        setInternalProviderId(program.key)
        setContentId(program.key)
        setInternalProviderData(fingerprint.encodeToByteArray())
    }.build().toContentValues()

    /**
     * Where the launcher can read the card's picture: the artwork cache, which the library fills as it
     * finds artwork. Publishing never waits for the network; a picture cached later is written then.
     */
    private fun artworkUri(artwork: WatchNextArtwork): Uri? =
        this.artwork.cached(artwork.locator)?.toFile()?.let { ArtworkProvider.uriFor(application.packageName, it) }

    companion object {
        private const val FIELD = "\u001F"

        /** The system feature every Fire TV device declares (Amazon's Fire TV device documentation). */
        const val FIRE_TV_FEATURE = "amazon.hardware.fire_tv"

        /**
         * Programs are written where a launcher shows them: Android TV and Google TV, which have the TV
         * provider. Fire OS has the provider too, but its home screen does not show these rows and Amazon's own
         * "continue" integration is closed to Reflux, so there the titles of personal media would sit in a
         * system provider for no one (DECISIONS.md, 2026-10-18). Reflux's own watch state is the same either way.
         */
        fun publishes(tvProvider: Boolean, fireOs: Boolean): Boolean = tvProvider && !fireOs
    }
}
