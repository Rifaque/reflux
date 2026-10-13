package dev.reflux.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.reflux.core.metadata.Credit
import dev.reflux.core.metadata.CreditRole
import dev.reflux.core.model.ArtworkLocator
import dev.reflux.core.model.MediaId
import dev.reflux.core.versions.SelectionCriterion
import dev.reflux.design.FormFactor
import dev.reflux.design.MaterialRole
import dev.reflux.ui.LocalChromeInsets
import dev.reflux.ui.ReportPageScroll
import dev.reflux.ui.components.ArtworkImage
import dev.reflux.ui.components.Button
import dev.reflux.ui.components.ButtonStyle
import dev.reflux.ui.components.Chip
import dev.reflux.ui.components.Icon
import dev.reflux.ui.components.IconButton
import dev.reflux.ui.components.Pressable
import dev.reflux.ui.components.ProgressBar
import dev.reflux.ui.components.SectionHeader
import dev.reflux.ui.components.TitleBackdrop
import dev.reflux.ui.components.TitlePlaceholder
import dev.reflux.ui.components.initialFocus
import dev.reflux.ui.components.titleTones
import dev.reflux.ui.state.AppState
import dev.reflux.ui.state.Destination
import dev.reflux.ui.state.DetailsModel
import dev.reflux.ui.state.Formatting
import dev.reflux.ui.state.MediaCard
import dev.reflux.ui.state.VersionRow
import dev.reflux.ui.state.detailsModel
import dev.reflux.ui.state.libraryState
import dev.reflux.ui.theme.LocalLayout
import dev.reflux.ui.theme.LocalTypography
import dev.reflux.ui.theme.RefluxColors
import dev.reflux.ui.theme.Text
import dev.reflux.ui.theme.glass

/** A movie or a show: what it is, what the main button will play, and which versions or episodes exist. */
@Composable
internal fun DetailsScreen(app: AppState, itemId: MediaId) {
    val loaded by app.libraryState(itemId) { Loaded(detailsModel(itemId, app.platform.device)) }
    // Null while loading; a loaded page without details is a work that has left the library.
    val details = (loaded ?: return).details ?: return Gone(app)
    val layout = LocalLayout.current
    var season by rememberSaveable(itemId) { mutableIntStateOf(details.initialSeason) }
    val episodes = details.seasons.getOrNull(season)?.episodes.orEmpty()
    val list = rememberLazyListState()
    ReportPageScroll(remember { derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 0 } }.value)
    run {
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = LocalChromeInsets.current.bottom)) {
            item(key = "header") { Header(app, details) }
            if (details.versions.isNotEmpty()) {
                item(key = "versions") { Versions(app, details, Modifier.padding(horizontal = layout.safeMarginDp.dp).padding(top = 28.dp)) }
            }
            if (details.seasons.isNotEmpty()) {
                item(key = "seasons") {
                    Row(
                        Modifier.padding(top = 28.dp, bottom = 6.dp).horizontalScroll(rememberScrollState()).padding(horizontal = layout.safeMarginDp.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        details.seasons.forEachIndexed { index, model ->
                            Button(model.season.title, { season = index }, style = if (index == season) ButtonStyle.GLASS else ButtonStyle.QUIET)
                        }
                    }
                }
                items(episodes, key = { it.id.value }) { episode ->
                    EpisodeRow(app, episode, Modifier.padding(horizontal = layout.safeMarginDp.dp, vertical = 5.dp))
                }
            }
            val cast = details.credits.filter { it.role == CreditRole.ACTOR }.take(16)
            if (cast.isNotEmpty()) item(key = "cast") { Cast(cast, Modifier.padding(top = 28.dp)) }
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
private fun Header(app: AppState, details: DetailsModel) {
    val layout = LocalLayout.current
    val background = RefluxColors.background
    val card = details.card
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = maxWidth
        // A television's screen is its whole canvas: the header leaves room for what follows it to show.
        val television = layout.formFactor == FormFactor.TV
        Box(Modifier.fillMaxWidth().heightIn(min = if (television) 424.dp else 520.dp)) {
            ArtworkImage(details.backdrop, width, Modifier.matchParentSize(), alignment = Alignment.TopCenter) { TitleBackdrop(card.title) }
            Box(Modifier.matchParentSize().background(Brush.horizontalGradient(0f to background.copy(alpha = 0.9f), 0.6f to background.copy(alpha = 0.45f), 1f to background.copy(alpha = 0.1f))))
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to background.copy(alpha = 0.6f), 0.25f to Color.Transparent, 0.55f to background.copy(alpha = 0.3f), 1f to background)))
            Row(
                Modifier.align(Alignment.BottomStart).padding(horizontal = layout.safeMarginDp.dp).padding(top = LocalChromeInsets.current.top + 24.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                if (width >= 760.dp) {
                    val shape = RoundedCornerShape(14.dp)
                    val posterWidth = if (television) 164.dp else 210.dp
                    Box(Modifier.width(posterWidth).aspectRatio(2f / 3f).shadow(24.dp, shape).clip(shape)) {
                        ArtworkImage(card.poster, posterWidth, Modifier.fillMaxSize()) { TitlePlaceholder(card.title, card.caption) }
                    }
                }
                Column(Modifier.weight(1f, fill = false).widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(card.title, style = LocalTypography.current.display, maxLines = 2)
                    details.tagline?.takeIf { it.isNotBlank() }?.let { Text(it, style = LocalTypography.current.title, color = RefluxColors.textSecondary, maxLines = 1) }
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (details.facts.isNotEmpty()) {
                            Text(details.facts.joinToString("   ·   "), style = LocalTypography.current.bodyStrong, color = RefluxColors.textSecondary, maxLines = 1)
                        }
                        details.badges.forEach { Chip(it) }
                        if (card.offline) Chip("Offline")
                    }
                    if (details.genres.isNotEmpty()) Text(details.genres.joinToString("  ·  "), style = LocalTypography.current.caption, color = RefluxColors.textTertiary, maxLines = 1)
                    details.overview?.takeIf { it.isNotBlank() }?.let { Text(it, color = RefluxColors.textSecondary, maxLines = 5) }
                    credit(details.credits)?.let { Text(it, style = LocalTypography.current.caption, color = RefluxColors.textTertiary, maxLines = 1) }
                    // Whether it has been started, and how much is left, said once and quietly.
                    val progress = Formatting.progress(card.entry.watchState)
                    val remaining = Formatting.remaining(card.entry.watchState)
                    if (progress != null && remaining != null) {
                        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            ProgressBar(progress, Modifier.width(200.dp))
                            Text(remaining, style = LocalTypography.current.caption, color = RefluxColors.textSecondary, maxLines = 1)
                        }
                    }
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        val playId = details.playId
                        Button(details.playLabel, { playId?.let { app.play(it) } }, Modifier.initialFocus(), icon = Icon.PLAY, style = ButtonStyle.PRIMARY, enabled = playId != null && !card.offline)
                        if (details.resumes && playId != null) Button("From the start", { app.play(playId, resume = false) })
                        IconButton(
                            Icon.CHECK,
                            if (card.watched) "Mark as unwatched" else "Mark as watched",
                            { app.setWatched(card.id, !card.watched) },
                            glass = true,
                            tint = if (card.watched) RefluxColors.accent else RefluxColors.textPrimary,
                        )
                        IconButton(
                            if (card.entry.favorite) Icon.HEART_FILLED else Icon.HEART,
                            if (card.entry.favorite) "Remove from favorites" else "Add to favorites",
                            { app.toggleFavorite(card.entry) },
                            glass = true,
                            tint = if (card.entry.favorite) RefluxColors.accent else RefluxColors.textPrimary,
                        )
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

/** Every version of a movie, best first, with why the first one is the one Play uses. */
@Composable
private fun Versions(app: AppState, details: DetailsModel, modifier: Modifier = Modifier) {
    Column(modifier.widthIn(max = 880.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(if (details.versions.size > 1) "Versions" else "Version")
        details.versions.forEach { version -> VersionItem(version, details.bestReason) { app.play(details.card.id, version.ranked.version.id) } }
    }
}

@Composable
private fun VersionItem(row: VersionRow, bestReason: SelectionCriterion?, onPlay: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Pressable(onPlay, Modifier.fillMaxWidth(), shape, enabled = row.ranked.playable, lift = 0.15f) { active ->
        Row(
            Modifier.fillMaxWidth().glass(MaterialRole.ELEVATED, shape).background(Color.White.copy(alpha = if (active) 0.06f else 0f))
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icon.PLAY, size = 16.dp, tint = if (row.ranked.playable) RefluxColors.textPrimary else RefluxColors.textTertiary)
            // Where there is no room beside the facts, the reason this version is the best one goes beneath them.
            val narrow = LocalLayout.current.formFactor == FormFactor.PHONE
            val reason = bestReason?.takeIf { row.best }?.let(Formatting::criterion)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(row.facts.joinToString("  ·  "), style = LocalTypography.current.bodyStrong, color = if (row.ranked.playable) RefluxColors.textPrimary else RefluxColors.textTertiary, maxLines = 1)
                Text((listOf(row.sourceName) + row.notes).joinToString("  ·  "), style = LocalTypography.current.caption, color = RefluxColors.textTertiary, maxLines = 1)
                if (narrow && reason != null) Text(reason, style = LocalTypography.current.caption, color = RefluxColors.textSecondary, maxLines = 1)
            }
            if (reason != null) {
                if (!narrow) Text(reason, style = LocalTypography.current.caption, color = RefluxColors.textSecondary, maxLines = 1)
                Chip("Recommended", emphasized = true)
            }
        }
    }
}

@Composable
private fun EpisodeRow(app: AppState, episode: MediaCard, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Pressable({ app.play(episode.id) }, modifier.widthIn(max = 980.dp).fillMaxWidth(), shape, enabled = !episode.offline, lift = 0.15f) { active ->
        Row(
            Modifier.fillMaxWidth().background(Color.White.copy(alpha = if (active) 0.07f else 0f)).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(176.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp))) {
                ArtworkImage(episode.wide, 176.dp, Modifier.fillMaxSize()) { EpisodePlaceholder(episode) }
                episode.progress?.let { ProgressBar(it, Modifier.align(Alignment.BottomCenter).padding(6.dp)) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(episode.caption ?: episode.title, style = LocalTypography.current.bodyStrong, color = if (episode.offline) RefluxColors.textTertiary else RefluxColors.textPrimary, maxLines = 1)
                val facts = listOfNotNull(
                    "Offline".takeIf { episode.offline },
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
                Column(Modifier.width(104.dp), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(84.dp).clip(CircleShape).background(RefluxColors.surfaceRaised), contentAlignment = Alignment.Center) {
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
