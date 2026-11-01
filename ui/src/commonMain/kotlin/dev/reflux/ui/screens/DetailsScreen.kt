package dev.reflux.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.border
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.reflux.core.metadata.Credit
import dev.reflux.core.metadata.CreditRole
import dev.reflux.core.offline.DownloadState
import dev.reflux.core.model.ArtworkLocator
import dev.reflux.core.model.MediaId
import dev.reflux.core.versions.SelectionCriterion
import dev.reflux.design.FormFactor
import dev.reflux.ui.LocalChromeInsets
import dev.reflux.ui.ReportPageScroll
import dev.reflux.ui.components.ArtworkImage
import dev.reflux.ui.components.Tab
import androidx.compose.ui.graphics.lerp
import dev.reflux.ui.components.rememberArtwork
import dev.reflux.ui.components.rememberPageTone
import dev.reflux.ui.components.Button
import dev.reflux.ui.components.ButtonStyle
import dev.reflux.ui.components.Chip
import dev.reflux.ui.components.Icon
import dev.reflux.ui.components.IconButton
import dev.reflux.ui.components.LocalFocusEntry
import dev.reflux.ui.components.Pressable
import dev.reflux.ui.components.ProgressBar
import dev.reflux.ui.components.ProgressRing
import dev.reflux.ui.components.SectionHeader
import dev.reflux.ui.components.TitleBackdrop
import dev.reflux.ui.components.TitlePlaceholder
import dev.reflux.ui.components.heading
import dev.reflux.ui.components.initialFocus
import dev.reflux.ui.components.pane
import dev.reflux.ui.components.rememberFocusReturn
import dev.reflux.ui.components.titleTones
import dev.reflux.ui.state.AppState
import dev.reflux.ui.state.ArtworkChoice
import dev.reflux.ui.state.Destination
import dev.reflux.ui.state.DetailsModel
import dev.reflux.ui.state.Formatting
import dev.reflux.ui.state.MediaCard
import dev.reflux.ui.state.EpisodeOffline
import dev.reflux.ui.state.OfflineStatus
import dev.reflux.ui.state.episodeOffline
import dev.reflux.ui.state.offlineState
import dev.reflux.ui.state.offlineStatus
import dev.reflux.ui.state.VersionRow
import dev.reflux.ui.state.detailsModel
import dev.reflux.ui.state.libraryState
import dev.reflux.ui.theme.LocalLayout
import dev.reflux.ui.theme.LocalTypography
import dev.reflux.ui.theme.RefluxColors
import dev.reflux.ui.theme.Text

/**
 * A movie, a show, or an episode: what it is, what the main button will play, and which versions or episodes
 * exist. An episode's own page is its versions under its show's name, reached from its row on the show's page.
 */
@Composable
internal fun DetailsScreen(app: AppState, itemId: MediaId) {
    val loaded by app.libraryState(itemId) { Loaded(detailsModel(itemId, app.platform.device, app.viewer)) }
    // Null while loading; a loaded page without details is a work that has left the library.
    val details = (loaded ?: return).details ?: return Gone(app)
    val layout = LocalLayout.current
    var season by rememberSaveable(itemId) { mutableIntStateOf(details.initialSeason) }
    val shown = details.seasons.getOrNull(season)
    val episodes = shown?.episodes.orEmpty()
    // Whether it is, or is being, kept on this device; null where the platform keeps nothing.
    val offline by app.offlineState(itemId) { app.platform.offline?.let { offlineStatus(itemId, it) } }
    // For a show: the season shown, and each of its episodes that can be or is kept.
    val seasonId = shown?.season?.id
    val seasonOffline by app.offlineState(seasonId) { seasonId?.let { id -> app.platform.offline?.let { offlineStatus(id, it) } } }
    val episodeMarks by app.offlineState(itemId, details.seasons.isNotEmpty()) {
        app.platform.offline?.takeIf { details.seasons.isNotEmpty() }?.let { episodeOffline(itemId, it) }
    }
    val list = rememberLazyListState()
    ReportPageScroll(remember { derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 0 } }.value)
    BoxWithConstraints(Modifier.fillMaxSize().pane(details.card.title)) {
        val television = layout.formFactor == FormFactor.TV
        // The title and Play are in the first view however short the window (a phone on its side): the header
        // never asks for more than the screen leaves above the bottom chrome.
        val headerMin = minOf(if (television) 424.dp else 560.dp, maxHeight - LocalChromeInsets.current.bottom - 16.dp)
        // The poster beside the title needs height as well as width.
        val room = maxWidth >= 760.dp && (television || maxHeight >= 600.dp)
        val art = ArtworkChoice.header(details.card.poster, details.backdrop, maxWidth / headerMin.coerceAtLeast(1.dp), room)
        // The page takes the atmosphere of the artwork across the header down to its end, then fades to the plain
        // base. With a generated header the page takes the poster's, so it agrees with the artwork shown on it.
        val fill = rememberArtwork(art.fill, maxWidth)
        val poster = rememberArtwork(details.card.poster, 220.dp)
        val atmosphere = rememberPageTone(fill ?: poster, details.card.title)
        var headerHeight by remember { mutableStateOf(520.dp) }
        val density = LocalDensity.current
        // The atmosphere recedes as the header scrolls away; versions, episodes, and cast below sit on the plain base.
        val tone = { lerp(RefluxColors.background, atmosphere, list.artworkPresence(with(density) { headerHeight.toPx() })) }
        PageAtmosphere(tone, headerHeight)
        val section = if (layout.formFactor == FormFactor.PHONE) 28.dp else 40.dp
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = LocalChromeInsets.current.bottom)) {
            item(key = "header") {
                Header(app, details, offline, art.posterBeside, fill, poster, tone, headerMin, maxHeight, Modifier.onSizeChanged { headerHeight = with(density) { it.height.toDp() } })
            }
            if (details.versions.isNotEmpty()) {
                item(key = "versions") { Versions(app, details, Modifier.padding(horizontal = layout.safeMarginDp.dp).padding(top = section)) }
            }
            if (details.seasons.isNotEmpty()) {
                // Room for a choice of versions beside every row of a season where any episode has one, and for a
                // download mark where episodes can be kept, so the rows line up.
                val versions = shown?.versions.orEmpty()
                val marks = episodeMarks.orEmpty()
                val rowWidth = episodeRowWidth(room = versions.isNotEmpty(), keeps = marks.isNotEmpty())
                item(key = "seasons") {
                    val tabs: @Composable () -> Unit = {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = layout.safeMarginDp.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            details.seasons.forEachIndexed { index, model -> Tab(model.season.title, index == season) { season = index } }
                        }
                    }
                    // Keeping the whole season shown is at the end of its tabs' line, over the episodes' marks, so a
                    // remote goes straight up and down between them; beneath the tabs on a phone.
                    val keep = seasonOffline?.takeIf { it.offered }
                    when {
                        keep == null || seasonId == null -> Box(Modifier.padding(top = section, bottom = 10.dp)) { tabs() }
                        layout.formFactor == FormFactor.PHONE -> Column(Modifier.padding(top = section, bottom = 10.dp)) {
                            tabs()
                            SeasonDownload(app, seasonId, keep, Modifier.padding(horizontal = layout.safeMarginDp.dp))
                        }
                        else -> Row(
                            Modifier.widthIn(max = rowWidth + layout.safeMarginDp.dp * 2).fillMaxWidth().padding(top = section, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.weight(1f)) { tabs() }
                            SeasonDownload(app, seasonId, keep, Modifier.padding(start = if (television) 18.dp else 6.dp, end = layout.safeMarginDp.dp))
                        }
                    }
                }
                items(episodes, key = { it.id.value }) { episode ->
                    EpisodeItem(
                        app, episode, versions[episode.id], room = versions.isNotEmpty(), marks[episode.id], keeps = marks.isNotEmpty(),
                        Modifier.padding(horizontal = layout.safeMarginDp.dp, vertical = 5.dp),
                    )
                }
            }
            val cast = details.credits.filter { it.role == CreditRole.ACTOR }.take(16)
            if (cast.isNotEmpty()) item(key = "cast") { Cast(cast, Modifier.padding(top = section)) }
        }
    }
}

private class Loaded(val details: DetailsModel?)

/** Shown when the open work disappears, e.g. its last file was deleted or its source removed. */
@Composable
private fun Gone(app: AppState) {
    AmbientBackdrop()
    Box(Modifier.fillMaxSize().padding(top = LocalChromeInsets.current.top), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 460.dp).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("This is no longer in your library", style = LocalTypography.current.headline, align = TextAlign.Center)
            Text(
                "Its files were moved or deleted, or its source was removed. If they come back, so does everything you watched.",
                color = RefluxColors.textSecondary,
                align = TextAlign.Center,
            )
            Button("Back", { if (!app.navigator.back()) app.navigator.show(Destination.Home) }, Modifier.initialFocus(), icon = Icon.BACK, style = ButtonStyle.PRIMARY)
        }
    }
}

@Composable
private fun Header(
    app: AppState,
    details: DetailsModel,
    offline: OfflineStatus?,
    posterBeside: Boolean,
    fill: ImageBitmap?,
    poster: ImageBitmap?,
    tone: () -> Color,
    minHeight: Dp,
    viewport: Dp,
    modifier: Modifier = Modifier,
) {
    val layout = LocalLayout.current
    val card = details.card
    Box(modifier.fillMaxWidth()) {
        // A television's screen is its whole canvas: the header leaves room for what follows it to show.
        val television = layout.formFactor == FormFactor.TV
        Box(Modifier.fillMaxWidth().heightIn(min = minHeight)) {
            ArtworkImage(fill, Modifier.matchParentSize(), alignment = Alignment.TopCenter) { TitleBackdrop(card.title, poster = poster) }
            // Scrims keep the text readable and dissolve the backdrop into the page's own tone.
            Box(Modifier.matchParentSize().artworkScrims(tone, left = 0.92f, right = 0.08f))
            Row(
                Modifier.align(Alignment.BottomStart).padding(horizontal = layout.safeMarginDp.dp).padding(top = LocalChromeInsets.current.top + 24.dp),
                horizontalArrangement = Arrangement.spacedBy(36.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                if (posterBeside) {
                    val shape = RoundedCornerShape(14.dp)
                    val posterWidth = if (television) 164.dp else 220.dp
                    // The title is beside it, and is said there.
                    Box(Modifier.width(posterWidth).aspectRatio(2f / 3f).shadow(32.dp, shape).clip(shape).clearAndSetSemantics { }) {
                        ArtworkImage(card.poster, posterWidth, Modifier.fillMaxSize()) { TitlePlaceholder(card.title, card.caption) }
                        Box(Modifier.matchParentSize().border(1.dp, Color.White.copy(alpha = 0.08f), shape))
                    }
                }
                Column(Modifier.weight(1f, fill = false).widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // The details are where a title is read in full: one too long for two lines at display size is set
                    // smaller, step by step, rather than cut off (it is cut off everywhere else).
                    var titleStep by remember(card.title) { mutableIntStateOf(0) }
                    val typography = LocalTypography.current
                    Text(
                        card.title,
                        Modifier.heading(),
                        style = listOf(typography.display, typography.headline, typography.title)[titleStep],
                        maxLines = listOf(2, 5, 8)[titleStep],
                        onTextLayout = { if (it.hasVisualOverflow && titleStep < 2) titleStep++ },
                    )
                    details.tagline?.takeIf { it.isNotBlank() }?.let { Text(it, style = LocalTypography.current.title.copy(fontWeight = FontWeight.Normal), color = RefluxColors.textSecondary, maxLines = 1) }
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (details.facts.isNotEmpty()) {
                            Text(details.facts.joinToString("  ·  "), Modifier.padding(end = 4.dp), style = LocalTypography.current.metadata, color = RefluxColors.textSecondary, maxLines = 1)
                        }
                        details.badges.forEach { Chip(it) }
                        if (card.offline) Chip("Not connected")
                    }
                    // A short screen keeps what it takes to choose and play; the quietest lines wait for a taller one.
                    val short = viewport < 600.dp
                    if (details.genres.isNotEmpty() && !short) Text(details.genres.joinToString("  ·  "), style = LocalTypography.current.caption, color = RefluxColors.textTertiary, maxLines = 1)
                    details.overview?.takeIf { it.isNotBlank() }?.let { Text(it, Modifier.widthIn(max = 680.dp).padding(top = 2.dp), color = RefluxColors.textSecondary, maxLines = if (short) 2 else 5) }
                    credit(details.credits)?.takeUnless { short }?.let { Text(it, style = LocalTypography.current.caption, color = RefluxColors.textTertiary, maxLines = 1) }
                    // Whether it has been started, and how much is left, said once and quietly.
                    val progress = Formatting.progress(card.entry.watchState)
                    val remaining = Formatting.remaining(card.entry.watchState)
                    if (progress != null && remaining != null) {
                        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            ProgressBar(progress, Modifier.width(200.dp))
                            Text(remaining, style = LocalTypography.current.caption, color = RefluxColors.textSecondary, maxLines = 1)
                        }
                    }
                    // On a narrow screen the lesser actions go to a second line rather than off the edge. On a
                    // television the focused action grows and wears a ring, so its neighbours stand further off.
                    val gap = if (LocalLayout.current.formFactor == FormFactor.TV) 22.dp else 12.dp
                    FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(10.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                        val playId = details.playId
                        Button(details.playLabel, { playId?.let { app.play(it) } }, Modifier.initialFocus(), icon = Icon.PLAY, style = ButtonStyle.PRIMARY, enabled = playId != null && !card.offline)
                        if (details.resumes && playId != null) Button("From the start", { app.play(playId, resume = false) })
                        IconButton(
                            Icon.CHECK,
                            if (card.watched) "Mark as unwatched" else "Mark as watched",
                            { app.setWatched(card.id, !card.watched) },
                            glass = true,
                            selected = card.watched,
                        )
                        IconButton(
                            if (card.entry.favorite) Icon.HEART_FILLED else Icon.HEART,
                            if (card.entry.favorite) "Remove from favorites" else "Add to favorites",
                            { app.toggleFavorite(card.entry) },
                            glass = true,
                        )
                        // A film, an episode on its own page, or a show (its next few episodes); a season is kept from beside its tabs.
                        if (offline?.offered == true) {
                            // The sheet this opens takes focus; when it closes, focus comes back here, not to Play.
                            val download = remember { FocusRequester() }
                            val entry = LocalFocusEntry.current
                            IconButton(
                                if (offline.downloaded) Icon.DOWNLOADED else Icon.DOWNLOAD,
                                when {
                                    offline.downloaded -> "Downloaded"
                                    offline.busy -> "Downloading"
                                    offline.failed -> "Download didn't finish"
                                    else -> "Download"
                                },
                                // What Reflux would keep, to confirm, and what of it is kept already, with what can be done about it.
                                {
                                    entry?.afterPanel = download
                                    app.reviewDownload(card.id)
                                },
                                Modifier.focusRequester(download),
                                glass = true,
                                selected = offline.downloaded,
                            )
                        }
                        // Telling Reflux what this is, when it guessed wrong; offered more plainly below when it is unsure.
                        // An episode is identified through its show, which its page leads back to.
                        val showId = details.showId
                        if (showId != null) {
                            Button("All episodes", { app.navigator.open(Destination.Details(showId)) }, style = ButtonStyle.QUIET)
                        } else if (app.canIdentify && !details.weak) {
                            Button("Identify", { app.navigator.open(Destination.Identify(card.id)) }, style = ButtonStyle.QUIET)
                        }
                    }
                    if (app.canIdentify && details.weak) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Reflux isn't sure what this is.", style = LocalTypography.current.caption, color = RefluxColors.textSecondary, maxLines = 1)
                            Button("Identify", { app.navigator.open(Destination.Identify(card.id)) }, icon = Icon.SEARCH)
                        }
                    }
                    // Said once, quietly, like progress: that it is on this device, or on its way.
                    offline?.label?.let { label ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            offline.progress?.let { ProgressBar(it, Modifier.width(120.dp)) }
                            Text(label, style = LocalTypography.current.caption, color = if (offline.failed) RefluxColors.unavailable else RefluxColors.textSecondary, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** "Directed by …" for movies, "Created by …" for shows. */
private fun credit(credits: List<Credit>): String? {
    val directors = credits.filter { it.role == CreditRole.DIRECTOR }.map { it.name }.distinct()
    if (directors.isNotEmpty()) return "Directed by ${directors.take(3).joinToString()}"
    val creators = credits.filter { it.role == CreditRole.CREATOR }.map { it.name }.distinct()
    return if (creators.isNotEmpty()) "Created by ${creators.take(3).joinToString()}" else null
}

/**
 * Every version of a movie or an episode, best first. The question this answers is "which one should I watch?": each copy
 * leads with what it is chosen by (resolution, HDR, edition), the one Play will use says so and why, and
 * where it lives and how it is encoded follow quietly beneath.
 */
@Composable
private fun Versions(app: AppState, details: DetailsModel, modifier: Modifier = Modifier) {
    val several = details.versions.size > 1
    Column(modifier.widthIn(max = 880.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.padding(bottom = 2.dp)) {
            SectionHeader(if (several) "Versions" else "Version")
            // Where the user's choice is set aside this time, that is what needs saying, in place of the general rule.
            val note = details.pinNote ?: "Play picks the best copy for this device. Choose another to play it instead.".takeIf { several }
            note?.let { Text(it, style = LocalTypography.current.caption, color = if (details.pinNote != null) RefluxColors.textSecondary else RefluxColors.textTertiary) }
        }
        // Keyed by version: choosing one reorders the list, and focus goes with the version it is on rather than
        // staying in its place, where a second press would choose another.
        details.versions.forEach { version -> key(version.ranked.version.id.value) {
            VersionItem(
                version,
                details.bestReason,
                onPlay = { app.play(details.card.id, version.ranked.version.id) },
                onPin = { app.pinVersion(details.card.id, version.ranked.version.id.takeUnless { version.pinned }) },
            )
        } }
    }
}

/**
 * A version and, where it can be, the standing choice of it: "Always play this", or "Let Reflux choose" on the
 * one already chosen. Beside the version, or beneath it on a phone, where the version needs the width.
 */
@Composable
private fun VersionItem(row: VersionRow, bestReason: SelectionCriterion?, onPlay: () -> Unit, onPin: () -> Unit) {
    val narrow = LocalLayout.current.formFactor == FormFactor.PHONE
    val choice: @Composable () -> Unit = {
        if (row.pinned || row.pinnable) Button(if (row.pinned) "Let Reflux choose" else "Always play this", onPin, style = ButtonStyle.QUIET)
    }
    if (narrow) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.End) {
            VersionCard(row, bestReason, onPlay, narrow)
            choice()
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { VersionCard(row, bestReason, onPlay, narrow) }
            // The same room beside every version, whatever it says or whether it says anything, so the versions
            // line up as one list.
            Box(Modifier.width(188.dp), contentAlignment = Alignment.CenterStart) { choice() }
        }
    }
}

@Composable
private fun VersionCard(row: VersionRow, bestReason: SelectionCriterion?, onPlay: () -> Unit, narrow: Boolean) {
    val shape = RoundedCornerShape(16.dp)
    val playable = row.ranked.playable
    // The one Play uses is lifted out of the list; the rest are outlines of the same shape.
    val chosen = row.best && bestReason != null
    val pinned = chosen && bestReason == SelectionCriterion.USER_PREFERENCE
    Pressable(onPlay, Modifier.fillMaxWidth(), shape, enabled = playable, lift = 0.15f, actionLabel = "Play") { active ->
        Row(
            Modifier.fillMaxWidth()
                .background(Color.White.copy(alpha = (if (chosen) 0.06f else 0f) + (if (active) 0.06f else 0f)))
                .border(1.dp, Color.White.copy(alpha = if (chosen) 0.14f else 0.07f), shape)
                .padding(horizontal = 18.dp, vertical = 15.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(34.dp).background(if (chosen && playable) RefluxColors.primaryAction else Color.White.copy(alpha = 0.08f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icon.PLAY,
                    size = 14.dp,
                    tint = when {
                        !playable -> RefluxColors.textTertiary
                        chosen -> RefluxColors.onPrimaryAction
                        else -> RefluxColors.textPrimary
                    },
                )
            }
            // On a phone the mark goes above the quality, leaving the reason the whole width beneath it.
            val mark: @Composable () -> Unit = { Chip(if (pinned) "Your choice" else "Recommended", emphasized = true, icon = if (pinned) null else Icon.CHECK) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (chosen && narrow) Box(Modifier.padding(bottom = 4.dp)) { mark() }
                Text(row.quality.joinToString("  ·  "), style = LocalTypography.current.title, color = if (playable) RefluxColors.textPrimary else RefluxColors.textTertiary, maxLines = 1)
                // Why this is the one: a user's own choice says so in their terms, Best Version gives its reason.
                if (chosen) {
                    Text(
                        if (pinned) "Play always uses this one" else Formatting.criterion(bestReason!!),
                        style = LocalTypography.current.metadata,
                        color = RefluxColors.textSecondary,
                        maxLines = 1,
                    )
                } else if (row.pinned) {
                    // The user's choice, set aside this time by something that outranks it (the cut being resumed,
                    // a source that is away): said, so the choice is not mistaken for forgotten.
                    Text("Your choice", style = LocalTypography.current.metadata, color = RefluxColors.textSecondary, maxLines = 1)
                }
                // Where it comes from matters more than how it is encoded.
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(row.sourceName, style = LocalTypography.current.captionStrong, color = RefluxColors.textSecondary, maxLines = 1)
                    val rest = row.technical + row.notes
                    if (rest.isNotEmpty()) Text("·  " + rest.joinToString("  ·  "), style = LocalTypography.current.caption, color = RefluxColors.textTertiary, maxLines = 1)
                }
            }
            if (chosen && !narrow) mark()
        }
    }
}

/**
 * An episode and, beside it, the way to its versions when it has several ("2 versions", its own page) and its
 * download mark where episodes of the show can be kept: where a remote reaches them with a step to the right
 * and their focus stays clear of the episode's own. On a phone the mark is at the row's end and the versions
 * beneath it. [room] keeps the versions' place beside every row of the season, and [keeps] the mark's, so the
 * rows line up.
 */
@Composable
private fun EpisodeItem(app: AppState, episode: MediaCard, versions: Int?, room: Boolean, mark: EpisodeOffline?, keeps: Boolean, modifier: Modifier = Modifier) {
    val choice: @Composable () -> Unit = {
        if (versions != null) {
            // Back from the episode's page comes back here, not to the top of the show's.
            val back = rememberFocusReturn("versions/${episode.id.value}")
            Button("$versions versions", { back.leaving(); app.navigator.open(Destination.Details(episode.id)) }, back.modifier, style = ButtonStyle.QUIET)
        }
    }
    val download: @Composable () -> Unit = { Box(Modifier.width(MarkWidth), contentAlignment = Alignment.Center) { mark?.let { EpisodeDownload(app, episode, it) } } }
    when {
        !room && !keeps -> EpisodeRow(app, episode, modifier.widthIn(max = 980.dp))
        LocalLayout.current.formFactor == FormFactor.PHONE -> Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.End) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                EpisodeRow(app, episode, Modifier.weight(1f))
                if (keeps) download()
            }
            if (room) choice()
        }
        // A focused control grows; on a television the gap leaves its ring clear of the row beside it.
        else -> Row(
            modifier.widthIn(max = episodeRowWidth(room, keeps)).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(if (LocalLayout.current.formFactor == FormFactor.TV) 24.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EpisodeRow(app, episode, Modifier.weight(1f))
            if (room) Box(Modifier.width(160.dp), contentAlignment = Alignment.CenterStart) { choice() }
            if (keeps) download()
        }
    }
}

/** The room a download mark takes beside an episode, focused or not. */
private val MarkWidth = 56.dp

/** How wide an episode's line is at most, with what is beside it. */
private fun episodeRowWidth(room: Boolean, keeps: Boolean): Dp = 980.dp + (if (room) 184.dp else 0.dp) + (if (keeps) MarkWidth + 24.dp else 0.dp)

/**
 * Whether an episode is kept, on its way, or can be: one control that opens what Reflux would keep of it, or
 * the copy there is and what can be done about it. Progress is a thin ring that moves only with the download.
 */
@Composable
private fun EpisodeDownload(app: AppState, episode: MediaCard, mark: EpisodeOffline) {
    val requester = remember { FocusRequester() }
    val entry = LocalFocusEntry.current
    val name = episode.caption ?: episode.title
    val state = mark.state
    val busy = state == DownloadState.QUEUED || state == DownloadState.PREPARING || state == DownloadState.DOWNLOADING || state == DownloadState.PAUSED
    val stopped = state == DownloadState.FAILED || state == DownloadState.UNAVAILABLE
    val description = when {
        state == DownloadState.COMPLETED -> "$name is on this device"
        state == DownloadState.PAUSED -> "Download of $name paused"
        busy -> "Downloading $name"
        stopped -> "Download of $name didn't finish"
        else -> "Download $name"
    }
    val target = maxOf(40f, minOf(LocalLayout.current.minTargetDp, 56f)).dp
    Pressable(
        {
            // The sheet takes focus; when it closes, focus comes back here.
            entry?.afterPanel = requester
            app.reviewDownload(episode.id)
        },
        Modifier.focusRequester(requester).semantics { contentDescription = description },
        CircleShape,
        lift = 0.8f,
    ) { active ->
        Box(
            Modifier.size(target).background(Color.White.copy(alpha = (if (state == DownloadState.COMPLETED) 0.18f else 0f) + (if (active) 0.14f else 0f))),
            contentAlignment = Alignment.Center,
        ) {
            if (busy) ProgressRing(mark.progress ?: 0f, Modifier.size(target * 0.74f), color = if (state == DownloadState.PAUSED) RefluxColors.textTertiary else RefluxColors.textPrimary)
            Icon(
                if (state == DownloadState.COMPLETED) Icon.DOWNLOADED else Icon.DOWNLOAD,
                size = target * if (busy) 0.36f else 0.46f,
                tint = when {
                    stopped -> RefluxColors.unavailable
                    state == null -> RefluxColors.textSecondary
                    else -> RefluxColors.textPrimary
                },
            )
        }
    }
}

/**
 * Keeping every episode of a season: "Download season", or "Season on this device" once all of it is, with how
 * much of it is kept beside it. It opens what Reflux would keep and the season's copies, for the user to confirm.
 */
@Composable
private fun SeasonDownload(app: AppState, seasonId: MediaId, status: OfflineStatus, modifier: Modifier = Modifier) {
    val requester = remember { FocusRequester() }
    val entry = LocalFocusEntry.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        status.label?.takeUnless { status.downloaded }?.let {
            Text(it, style = LocalTypography.current.caption, color = if (status.failed) RefluxColors.unavailable else RefluxColors.textSecondary, maxLines = 1)
        }
        status.progress?.let { ProgressBar(it, Modifier.width(72.dp)) }
        Button(
            if (status.downloaded) "Season on this device" else "Download season",
            {
                entry?.afterPanel = requester
                app.reviewDownload(seasonId)
            },
            Modifier.focusRequester(requester),
            icon = if (status.downloaded) Icon.DOWNLOADED else Icon.DOWNLOAD,
            style = ButtonStyle.QUIET,
        )
    }
}

@Composable
private fun EpisodeRow(app: AppState, episode: MediaCard, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Pressable(
        { app.play(episode.id) },
        modifier.fillMaxWidth().semantics { if (episode.watched) stateDescription = "Watched" },
        shape,
        enabled = !episode.offline,
        lift = 0.15f,
        actionLabel = "Play",
    ) { active ->
        Row(
            Modifier.fillMaxWidth().background(Color.White.copy(alpha = if (active) 0.07f else 0f)).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val thumbnail = RoundedCornerShape(10.dp)
            Box(Modifier.width(176.dp).aspectRatio(16f / 9f).clip(thumbnail)) {
                ArtworkImage(episode.wide, 176.dp, Modifier.fillMaxSize()) { EpisodePlaceholder(episode) }
                episode.progress?.let {
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f))))
                    ProgressBar(it, Modifier.align(Alignment.BottomCenter).padding(6.dp))
                }
                Box(Modifier.matchParentSize().border(1.dp, Color.White.copy(alpha = 0.07f), thumbnail))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Up to three lines: beside its picture on a phone, one line keeps only the number, and episodes are
                // told apart by name.
                Text(episode.caption ?: episode.title, style = LocalTypography.current.bodyStrong, color = if (episode.offline) RefluxColors.textTertiary else RefluxColors.textPrimary, maxLines = 3)
                val facts = listOfNotNull(
                    "Not connected".takeIf { episode.offline },
                    Formatting.remaining(episode.entry.watchState) ?: episode.entry.metadata?.runtimeMinutes?.takeIf { it > 0 }?.let(Formatting::runtime),
                )
                if (facts.isNotEmpty()) Text(facts.joinToString("  ·  "), style = LocalTypography.current.caption, color = RefluxColors.textTertiary, maxLines = 1)
                episode.entry.metadata?.overview?.takeIf { it.isNotBlank() }?.let { Text(it, style = LocalTypography.current.caption, color = RefluxColors.textSecondary, maxLines = 2) }
            }
            if (episode.watched) Icon(Icon.CHECK, Modifier.padding(end = 10.dp), size = 18.dp, tint = RefluxColors.textSecondary)
        }
    }
}

@Composable
private fun EpisodePlaceholder(episode: MediaCard) {
    val (light, dark) = titleTones(episode.title)
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(light, dark))), contentAlignment = Alignment.Center) {
        Icon(Icon.PLAY, size = 22.dp, tint = Color.White.copy(alpha = 0.7f))
    }
}

@Composable
private fun Cast(cast: List<Credit>, modifier: Modifier = Modifier) {
    val layout = LocalLayout.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader("Cast", Modifier.padding(horizontal = layout.safeMarginDp.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = layout.safeMarginDp.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            items(cast) { person ->
                // One stop: who, and whom they play. The picture, or the initials standing in for it, says nothing more.
                Column(Modifier.width(104.dp).semantics(mergeDescendants = true) { }, verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(84.dp).clip(CircleShape).background(RefluxColors.surfaceRaised).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
                        ArtworkImage(person.profileUrl?.let { ArtworkLocator.Remote(it) }, 84.dp, Modifier.fillMaxSize(), alignment = Alignment.TopCenter) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(initials(person.name), style = LocalTypography.current.title, color = RefluxColors.textSecondary)
                            }
                        }
                    }
                    Text(person.name, style = LocalTypography.current.captionStrong, maxLines = 1)
                    person.character?.takeIf { it.isNotBlank() }?.let { Text(it, style = LocalTypography.current.caption, color = RefluxColors.textTertiary, maxLines = 1) }
                }
            }
        }
    }
}

private fun initials(name: String): String =
    name.split(' ').filter { it.isNotBlank() }.let { parts -> listOfNotNull(parts.firstOrNull(), parts.drop(1).lastOrNull()) }
        .joinToString("") { it.first().uppercase() }
