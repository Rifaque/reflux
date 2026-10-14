package dev.reflux.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.reflux.core.model.ArtworkLocator
import dev.reflux.design.Motion
import dev.reflux.ui.state.ArtworkLoader
import dev.reflux.ui.state.Formatting
import dev.reflux.ui.state.MediaCard
import dev.reflux.ui.theme.LocalLayout
import dev.reflux.ui.theme.LocalTypography
import dev.reflux.ui.theme.RefluxColors
import dev.reflux.ui.theme.Text
import dev.reflux.ui.theme.tween
import kotlin.math.absoluteValue
import kotlin.math.ceil

val LocalArtworkLoader = staticCompositionLocalOf<ArtworkLoader> { error("No artwork loader provided") }

/** Decoded sizes are bucketed so that layout animations (the controller morph) do not reload images. */
private const val SIZE_BUCKET_PX = 160

/** Loads [locator] at about [width]; null while loading, when there is no artwork, or when it is unavailable. */
@Composable
fun rememberArtwork(locator: ArtworkLocator?, width: Dp): ImageBitmap? {
    val loader = LocalArtworkLoader.current
    val pixels = with(LocalDensity.current) { width.toPx() }
    val bucket = (ceil(pixels / SIZE_BUCKET_PX).toInt().coerceAtLeast(1)) * SIZE_BUCKET_PX
    var image by remember(locator, bucket) { mutableStateOf(locator?.let { loader.cached(it, bucket) }) }
    LaunchedEffect(locator, bucket) {
        if (image == null && locator != null) image = loader.load(locator, bucket)
    }
    return image
}

/** Artwork that fades in over [fallback], so a slow or missing image never leaves a hole. */
@Composable
fun ArtworkImage(
    locator: ArtworkLocator?,
    width: Dp,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    fallback: @Composable () -> Unit = {},
) = ArtworkImage(rememberArtwork(locator, width), modifier, alignment, fallback)

/** An already loaded [image], or [fallback] while there is none. */
@Composable
fun ArtworkImage(
    image: ImageBitmap?,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
    fallback: @Composable () -> Unit = {},
) {
    val alpha by animateFloatAsState(if (image != null) 1f else 0f, Motion.controlsShow.tween())
    Box(modifier) {
        if (alpha < 1f) fallback()
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha },
                alignment = alignment,
                contentScale = ContentScale.Crop,
            )
        }
    }
}

/** A stable hue for a title, so a work without artwork always gets the same color. */
fun titleHue(title: String): Float = (title.fold(7) { hash, char -> hash * 31 + char.code }.absoluteValue % 360).toFloat()

/** The two tones of a generated backdrop for [title]. */
fun titleTones(title: String): Pair<Color, Color> {
    val hue = titleHue(title)
    return Color.hsl(hue, 0.42f, 0.24f) to Color.hsl((hue + 38f) % 360f, 0.50f, 0.09f)
}

/**
 * What a work looks like before (or without) artwork: its title set on a color derived from the title.
 * Deliberately designed rather than a grey box, because a fresh library is all placeholders.
 */
/** A full-bleed generated backdrop for [title], for heroes and headers of works without artwork. */
@Composable
fun TitleBackdrop(title: String, modifier: Modifier = Modifier) {
    val (light, dark) = titleTones(title)
    Box(
        modifier.fillMaxSize()
            .background(Brush.linearGradient(listOf(dark, light, dark), start = Offset.Zero, end = Offset.Infinite))
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(light.copy(alpha = 0.9f), Color.Transparent),
                        center = Offset(size.width * 0.78f, size.height * 0.2f),
                        radius = size.maxDimension * 0.55f,
                    ),
                )
            },
    )
}

@Composable
fun TitlePlaceholder(title: String, caption: String?, modifier: Modifier = Modifier) {
    val (light, dark) = titleTones(title)
    Box(
        modifier.fillMaxSize()
            .background(Brush.linearGradient(listOf(light, dark), start = Offset.Zero, end = Offset.Infinite))
            .drawBehind {
                // A soft top-left sheen, the same light that edges the glass materials.
                drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.14f), Color.Transparent), center = Offset.Zero, radius = size.maxDimension * 0.9f))
            }
            .padding(12.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = LocalTypography.current.bodyStrong, maxLines = 4)
            if (caption != null) Text(caption, style = LocalTypography.current.caption, color = RefluxColors.textSecondary, maxLines = 1)
        }
    }
}

@Composable
private fun StateBadges(card: MediaCard, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        if (card.offline) {
            Box(Modifier.fillMaxSize().background(RefluxColors.background.copy(alpha = 0.55f)))
            Chip("Not connected", Modifier.align(Alignment.TopStart).padding(8.dp))
        }
        if (card.watched) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(8.dp).size(22.dp).clip(CircleShape).background(RefluxColors.background.copy(alpha = 0.72f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icon.CHECK, size = 13.dp)
            }
        }
        card.progress?.let { ProgressBar(it, Modifier.align(Alignment.BottomCenter).padding(horizontal = 8.dp, vertical = 8.dp)) }
    }
}

private fun describe(card: MediaCard): String = listOfNotNull(
    card.title,
    card.caption,
    "watched".takeIf { card.watched },
    "not connected".takeIf { card.offline },
    "on this device".takeIf { card.downloaded },
).joinToString(", ")

/**
 * A portrait card: poster, then title and caption beneath. Its width follows the adaptive layout, or the
 * space it is given when [fill] is set (grids).
 */
@Composable
fun PosterCard(card: MediaCard, onClick: () -> Unit, modifier: Modifier = Modifier, fill: Boolean = false, focusKey: String = card.id.value) {
    val focusReturn = rememberFocusReturn(focusKey)
    val layout = LocalLayout.current
    val width = layout.posterWidthDp.dp
    val shape = RoundedCornerShape(12.dp)
    val poster = rememberArtwork(card.poster, width)
    Column(if (fill) modifier.fillMaxWidth() else modifier.width(width), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Pressable(
            {
                focusReturn.leaving()
                onClick()
            },
            focusReturn.modifier.semantics { contentDescription = describe(card) },
            shape,
        ) { active ->
            Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
                ArtworkImage(poster, Modifier.fillMaxSize()) { TitlePlaceholder(card.title, null) }
                if (active) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.06f)))
                StateBadges(card)
            }
        }
        // Two lines are always reserved so rows line up; a placeholder already says the title, so only the caption follows it.
        Column(Modifier.padding(horizontal = 2.dp)) {
            if (poster != null) Text(card.title, style = LocalTypography.current.captionStrong, maxLines = 1)
            Caption(card.caption, card.downloaded)
            if (poster == null) Text(" ", style = LocalTypography.current.captionStrong, maxLines = 1)
        }
    }
}

/** A card's line beneath its title, with a small mark when a copy is kept on this device. */
@Composable
private fun Caption(text: String?, downloaded: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        if (downloaded) Icon(Icon.DOWNLOADED, size = 12.dp, tint = RefluxColors.textSecondary)
        Text(text ?: " ", style = LocalTypography.current.caption, color = RefluxColors.textTertiary, maxLines = 1)
    }
}

/** A landscape card for things in progress: the title sits on the image with what is left to watch. */
@Composable
fun WideCard(card: MediaCard, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val layout = LocalLayout.current
    val width = (layout.posterWidthDp * 1.9f).dp
    val shape = RoundedCornerShape(14.dp)
    Column(modifier.width(width), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Pressable(onClick, Modifier.semantics { contentDescription = describe(card) }, shape, lift = 0.6f) { active ->
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                ArtworkImage(card.wide ?: card.poster, width, Modifier.fillMaxSize(), alignment = Alignment.TopCenter) {
                    TitlePlaceholder(card.title, null)
                }
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(0.45f to Color.Transparent, 1f to RefluxColors.background.copy(alpha = 0.88f)),
                    ),
                )
                if (active) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.06f)))
                if (card.wide != null || card.poster != null) {
                    Text(
                        card.title,
                        Modifier.align(Alignment.BottomStart).padding(start = 12.dp, end = 12.dp, bottom = 20.dp),
                        style = LocalTypography.current.bodyStrong,
                        maxLines = 1,
                    )
                }
                StateBadges(card)
            }
        }
        Column(Modifier.padding(horizontal = 2.dp)) {
            Text(card.caption ?: card.title, style = LocalTypography.current.captionStrong, maxLines = 1)
            Text(
                Formatting.remaining(card.entry.watchState) ?: " ",
                style = LocalTypography.current.caption,
                color = RefluxColors.textTertiary,
                maxLines = 1,
            )
        }
    }
}
